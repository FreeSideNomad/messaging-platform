package com.acme.reliable.spi;

/**
 * Registry for command handlers.
 * Implementations should delegate to the appropriate handler based on command name.
 */
public interface HandlerRegistry {
    /**
     * Invoke the handler for the given command.
     *
     * @param commandName The name of the command
     * @param payload The command payload as JSON
     * @return The result as JSON
     */
    String invoke(String commandName, String payload);
}
