package com.acme.reliable.processor;

import com.acme.reliable.config.MessagingConfig;
import com.acme.reliable.config.TimeoutConfig;
import com.acme.reliable.core.Aggregates;
import com.acme.reliable.core.Envelope;
import com.acme.reliable.core.Jsons;
import com.acme.reliable.core.Outbox;
import com.acme.reliable.core.PermanentException;
import com.acme.reliable.core.RetryableBusinessException;
import com.acme.reliable.core.TransientException;
import com.acme.reliable.persistence.jdbc.service.CommandService;
import com.acme.reliable.persistence.jdbc.service.DlqService;
import com.acme.reliable.persistence.jdbc.service.InboxService;
import com.acme.reliable.persistence.jdbc.service.OutboxService;
import com.acme.reliable.spi.HandlerRegistry;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.time.Instant;

@Singleton
public class Executor {
    private final InboxService inbox;
    private final CommandService commands;
    private final OutboxService outboxStore;
    private final Outbox outbox;
    private final DlqService dlq;
    private final HandlerRegistry registry;
    private final FastPathPublisher fastPath;
    private final MessagingConfig messagingConfig;
    private final long leaseSeconds;

    public Executor(InboxService i, CommandService c, OutboxService os, Outbox o, DlqService d,
                    HandlerRegistry r, FastPathPublisher f, TimeoutConfig timeoutConfig,
                    MessagingConfig messagingConfig) {
        this.inbox = i;
        this.commands = c;
        this.outboxStore = os;
        this.outbox = o;
        this.dlq = d;
        this.registry = r;
        this.fastPath = f;
        this.messagingConfig = messagingConfig;
        this.leaseSeconds = timeoutConfig.getCommandLeaseSeconds();
    }

    @Transactional
    public void process(Envelope env) {
        if (!inbox.markIfAbsent(env.messageId().toString(), "CommandExecutor")) {
            return;
        }
        commands.markRunning(env.commandId(), Instant.now().plusSeconds(leaseSeconds));
        try {
            String resultJson = registry.invoke(env.name(), env.payload());
            commands.markSucceeded(env.commandId());
            var replyId = outboxStore.addReturningId(outbox.rowMqReply(env, "CommandCompleted", resultJson));
            var eventId = outboxStore.addReturningId(outbox.rowKafkaEvent(
                messagingConfig.getTopicNaming().buildEventTopic(env.name()),
                env.key(),
                "CommandCompleted",
                Aggregates.snapshot(env.key())
            ));
            // fastPath.registerAfterCommit(replyId); // DISABLED: causing transaction leak
            // fastPath.registerAfterCommit(eventId); // DISABLED: causing transaction leak
        } catch (PermanentException e) {
            commands.markFailed(env.commandId(), e.getMessage());
            dlq.park(env.commandId(), env.name(), env.key(), env.payload(), "FAILED", "Permanent", e.getMessage(), 0, "worker");
            var replyId = outboxStore.addReturningId(outbox.rowMqReply(env, "CommandFailed", Jsons.of("error", e.getMessage())));
            var eventId = outboxStore.addReturningId(outbox.rowKafkaEvent(
                messagingConfig.getTopicNaming().buildEventTopic(env.name()),
                env.key(),
                "CommandFailed",
                Jsons.of("error", e.getMessage())
            ));
            // fastPath.registerAfterCommit(replyId); // DISABLED: causing transaction leak
            // fastPath.registerAfterCommit(eventId); // DISABLED: causing transaction leak
            // Don't re-throw for permanent failures - we want to commit the DLQ entry and failure state
        } catch (RetryableBusinessException | TransientException e) {
            commands.bumpRetry(env.commandId(), e.getMessage());
            throw e;
        }
    }
}
