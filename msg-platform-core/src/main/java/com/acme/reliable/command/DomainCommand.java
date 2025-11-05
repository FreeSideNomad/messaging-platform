package com.acme.reliable.command;

/**
 * Marker interface for domain commands. Commands implementing this interface will be automatically
 * discovered and registered by the AutoCommandHandlerRegistry.
 *
 * <p>Convention: - Command class name should end with "Command" (e.g., CreateAccountCommand) - The
 * command type will be derived by removing the "Command" suffix (e.g., "CreateAccount") - Service
 * methods accepting the command as a single parameter will be auto-registered as handlers
 */
public interface DomainCommand {}
