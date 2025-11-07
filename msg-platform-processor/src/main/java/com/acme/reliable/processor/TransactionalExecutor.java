package com.acme.reliable.processor;

import com.acme.reliable.command.CommandExecutor;
import com.acme.reliable.command.CommandHandlerRegistry;
import com.acme.reliable.command.CommandMessage;
import com.acme.reliable.command.DomainCommand;
import com.acme.reliable.config.MessagingConfig;
import com.acme.reliable.config.TimeoutConfig;
import com.acme.reliable.core.Aggregates;
import com.acme.reliable.core.Envelope;
import com.acme.reliable.core.Jsons;
import com.acme.reliable.core.Outbox;
import com.acme.reliable.core.PermanentException;
import com.acme.reliable.core.RetryableBusinessException;
import com.acme.reliable.core.TransientException;
import com.acme.reliable.process.CommandReply;
import com.acme.reliable.processor.process.ProcessManager;
import com.acme.reliable.service.CommandService;
import com.acme.reliable.service.DlqService;
import com.acme.reliable.service.InboxService;
import com.acme.reliable.service.OutboxService;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class TransactionalExecutor implements CommandExecutor {
  private final InboxService inbox;
  private final CommandService commands;
  private final OutboxService outboxStore;
  private final Outbox outbox;
  private final DlqService dlq;
  private final CommandHandlerRegistry commandRegistry;
  private final FastPathPublisher fastPath;
  private final MessagingConfig messagingConfig;
  private final long leaseSeconds;
  private final ProcessManager processManager;

  public TransactionalExecutor(
      InboxService i,
      CommandService c,
      OutboxService os,
      Outbox o,
      DlqService d,
      CommandHandlerRegistry commandRegistry,
      FastPathPublisher f,
      TimeoutConfig timeoutConfig,
      MessagingConfig messagingConfig,
      ProcessManager processManager) {
    this.inbox = i;
    this.commands = c;
    this.outboxStore = os;
    this.outbox = o;
    this.dlq = d;
    this.commandRegistry = commandRegistry;
    this.fastPath = f;
    this.messagingConfig = messagingConfig;
    this.leaseSeconds = timeoutConfig.getCommandLeaseSeconds();
    this.processManager = processManager;
  }

  @Transactional
  public void process(Envelope env) {
    if (!inbox.markIfAbsent(env.messageId().toString(), "CommandExecutor")) {
      return;
    }
    commands.markRunning(env.commandId(), Instant.now().plusSeconds(leaseSeconds));
    try {
      String resultJson = tryStartProcess(env).orElseGet(() -> handleCommand(env));
      commands.markSucceeded(env.commandId());
      var replyId =
          outboxStore.addReturningId(outbox.rowMqReply(env, "CommandCompleted", resultJson));
      var eventId =
          outboxStore.addReturningId(
              outbox.rowKafkaEvent(
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
              outbox.rowMqReply(env, "CommandFailed", Jsons.of("error", e.getMessage())));
      var eventId =
          outboxStore.addReturningId(
              outbox.rowKafkaEvent(
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

  private Optional<String> tryStartProcess(Envelope env) {
    return processManager
        .findConfiguration(env.name())
        .flatMap(
            config -> {
              Class<? extends DomainCommand> initiationType = config.getInitiationCommandType();
              if (initiationType == null) {
                return Optional.empty();
              }

              DomainCommand initiationCommand = deserializeCommand(initiationType, env);
              Map<String, Object> initialState =
                  Optional.ofNullable(config.initializeProcessState(initiationCommand))
                      .orElseGet(java.util.HashMap::new);

              UUID processId;
              try {
                processId =
                    processManager.startProcess(config.getProcessType(), env.key(), initialState);
              } catch (RuntimeException e) {
                throw new PermanentException(
                    "Failed to start process '%s' for command '%s': %s"
                        .formatted(config.getProcessType(), env.name(), e.getMessage()),
                    e);
              }

              Map<String, Object> payload =
                  Map.of(
                      "processId", processId.toString(),
                      "processType", config.getProcessType(),
                      "status", "STARTED");

              return Optional.of(Jsons.toJson(payload));
            });
  }

  private String handleCommand(Envelope env) {
    CommandMessage commandMessage =
        new CommandMessage(env.commandId(), env.correlationId(), env.name(), env.payload());
    CommandReply reply = commandRegistry.handle(commandMessage);
    return reply.toJson();
  }

  private DomainCommand deserializeCommand(
      Class<? extends DomainCommand> commandType, Envelope env) {
    try {
      return Jsons.fromJson(env.payload(), commandType);
    } catch (Exception e) {
      throw new PermanentException(
          "Failed to parse initiation command for process: " + env.name(), e);
    }
  }
}
