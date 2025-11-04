package com.acme.payments.application.command;

import com.acme.payments.domain.model.Money;
import com.acme.reliable.command.DomainCommand;

import java.util.UUID;

/**
 * Command to book (reserve) limits for an account
 */
public record BookLimitsCommand(
    UUID accountId,
    Money amount
) implements DomainCommand {
}
