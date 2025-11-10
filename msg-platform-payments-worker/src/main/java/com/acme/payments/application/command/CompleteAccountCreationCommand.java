package com.acme.payments.application.command;

import com.acme.reliable.command.DomainCommand;

import java.util.UUID;

/**
 * Terminal command for account creation process. This is a no-op command that marks the completion
 * of account creation.
 */
public record CompleteAccountCreationCommand(UUID accountId) implements DomainCommand {
}
