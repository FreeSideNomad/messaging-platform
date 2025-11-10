package com.acme.reliable.processor;

import com.acme.reliable.command.CommandBus;
import com.acme.reliable.config.MessagingConfig;
import com.acme.reliable.core.Jsons;
import com.acme.reliable.domain.Outbox;
import com.acme.reliable.service.CommandService;
import com.acme.reliable.service.OutboxService;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.UUID;

@Singleton
public class TransactionalCommandBus implements CommandBus {
    private final CommandService commands;
    private final OutboxService outboxStore;
    private final FastPathPublisher fastPath;
    private final MessagingConfig messagingConfig;

    public TransactionalCommandBus(
            CommandService c, OutboxService os, FastPathPublisher f, MessagingConfig messagingConfig) {
        this.commands = c;
        this.outboxStore = os;
        this.fastPath = f;
        this.messagingConfig = messagingConfig;
    }

    @Transactional
    public UUID accept(
            String name, String idem, String bizKey, String payload, Map<String, String> reply) {
        if (commands.existsByIdempotencyKey(idem)) {
            throw new IllegalStateException("Duplicate idempotency key");
        }
        UUID id = commands.savePending(name, idem, bizKey, payload, Jsons.toJson(reply));
        long outboxId =
                outboxStore.addReturningId(Outbox.newCommandRequested(name, id, bizKey, payload, reply, messagingConfig));
        // fastPath.registerAfterCommit(outboxId); // DISABLED: causing transaction leak
        return id;
    }
}
