package com.acme.reliable.processor;

import com.acme.reliable.command.CommandExecutor;
import com.acme.reliable.command.CommandHandlerRegistry;
import com.acme.reliable.command.CommandMessage;
import com.acme.reliable.config.MessagingConfig;
import com.acme.reliable.config.TimeoutConfig;
import com.acme.reliable.core.Aggregates;
import com.acme.reliable.core.Envelope;
import com.acme.reliable.core.Jsons;
import com.acme.reliable.domain.Outbox;
import com.acme.reliable.core.PermanentException;
import com.acme.reliable.core.RetryableBusinessException;
import com.acme.reliable.core.TransientException;
import com.acme.reliable.process.CommandReply;
import com.acme.reliable.service.CommandService;
import com.acme.reliable.service.DlqService;
import com.acme.reliable.service.InboxService;
import com.acme.reliable.service.OutboxService;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.time.Instant;

@Singleton
public class TransactionalExecutor implements CommandExecutor {
  private final InboxService inbox;
  private final CommandService commands;
  private final OutboxService outboxStore;
  private final DlqService dlq;
  private final CommandHandlerRegistry registry;
  private final FastPathPublisher fastPath;
  private final MessagingConfig messagingConfig;
  private final long leaseSeconds;

  public TransactionalExecutor(
      InboxService i,
      CommandService c,
      OutboxService os,
      DlqService d,
      CommandHandlerRegistry r,
      FastPathPublisher f,
      TimeoutConfig timeoutConfig,
      MessagingConfig messagingConfig) {
    this.inbox = i;
    this.commands = c;
    this.outboxStore = os;
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
      // Create command message for new registry interface
      CommandMessage command = new CommandMessage(
          env.commandId(),
          env.correlationId(),
          env.name(),
          env.payload()
      );

      // Handle command and get reply
      CommandReply reply = registry.handle(command);

      // Convert reply data to JSON string
      String resultJson;
      if (reply.data() != null && !reply.data().isEmpty()) {
        resultJson = Jsons.toJson(reply.data());
      } else {
        resultJson = "{}";
      }

      commands.markSucceeded(env.commandId());
      var replyId =
          outboxStore.addReturningId(Outbox.newMqReply(env, "CommandCompleted", resultJson, messagingConfig));
      var eventId =
          outboxStore.addReturningId(
              Outbox.newKafkaEvent(
                  messagingConfig.getTopicNaming().buildEventTopic(env.name()),
                  env.key(),
                  "CommandCompleted",
                  Aggregates.snapshot(env.key())));
      // fastPath.registerAfterCommit(replyId); // DISABLED: causing transaction leak
      // fastPath.registerAfterCommit(eventId); // DISABLED: causing transaction leak
    } catch (PermanentException e) {
      commands.markFailed(env.commandId(), e.getMessage());
      dlq.park(
          env.commandId(),
          env.name(),
          env.key(),
          env.payload(),
          "FAILED",
          "Permanent",
          e.getMessage(),
          0,
          "worker");
      var replyId =
          outboxStore.addReturningId(
              Outbox.newMqReply(env, "CommandFailed", Jsons.of("error", e.getMessage()), messagingConfig));
      var eventId =
          outboxStore.addReturningId(
              Outbox.newKafkaEvent(
                  messagingConfig.getTopicNaming().buildEventTopic(env.name()),
                  env.key(),
                  "CommandFailed",
                  Jsons.of("error", e.getMessage())));
      // fastPath.registerAfterCommit(replyId); // DISABLED: causing transaction leak
      // fastPath.registerAfterCommit(eventId); // DISABLED: causing transaction leak
      // Don't re-throw for permanent failures - we want to commit the DLQ entry and failure state
    } catch (RetryableBusinessException | TransientException e) {
      commands.bumpRetry(env.commandId(), e.getMessage());
      throw e;
    }
  }
}
