package com.acme.reliable.command;

import com.acme.reliable.process.CommandReply;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry for command handlers - maps command types to their handlers. This is a generic
 * infrastructure component that can be used by any bounded context. Pure POJO - no framework
 * dependencies.
 */
public class CommandHandlerRegistry {
  private static final Logger log = LoggerFactory.getLogger(CommandHandlerRegistry.class);

  private final Map<String, Function<CommandMessage, CommandReply>> handlers = new HashMap<>();

  /**
   * Register a handler for a specific command type
   *
   * @throws IllegalStateException if a handler is already registered for this command type
   */
  public void registerHandler(String commandType, Function<CommandMessage, CommandReply> handler) {
    if (handlers.containsKey(commandType)) {
      String error = "Handler already registered for command type: " + commandType;
      log.error(error);
      throw new IllegalStateException(error);
    }
    log.info("Registering handler for command type: {}", commandType);
    handlers.put(commandType, handler);
  }

  /** Handle a command by delegating to the registered handler */
  public CommandReply handle(CommandMessage command) {
    log.info("Handling command: {} id={}", command.commandType(), command.commandId());

    Function<CommandMessage, CommandReply> handler = handlers.get(command.commandType());
    if (handler == null) {
      String error = "No handler registered for command type: " + command.commandType();
      log.error(error);
      return CommandReply.failed(command.commandId(), command.correlationId(), error);
    }

    try {
      CommandReply reply = handler.apply(command);
      log.info(
          "Command handled successfully: {} id={}", command.commandType(), command.commandId());
      return reply;
    } catch (Exception e) {
      log.error("Error handling command: {} id={}", command.commandType(), command.commandId(), e);
      return CommandReply.failed(command.commandId(), command.correlationId(), e.getMessage());
    }
  }
}
