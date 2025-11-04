package com.acme.payments.application.command;

import com.acme.payments.domain.model.Money;
import com.acme.reliable.command.DomainCommand;

import java.util.UUID;

/**
 * Compensation command to reverse previously booked limits
 */
public record ReverseLimitsCommand(
    UUID accountId,
    Money amount
) implements DomainCommand {
}
