package com.acme.payments.application.command;

import com.acme.payments.domain.model.AccountType;
import com.acme.reliable.command.DomainCommand;

import java.util.UUID;

/**
 * Command to create a new account
 */
public record CreateAccountCommand(
    UUID customerId,
    String currencyCode,
    String transitNumber,
    AccountType accountType,
    boolean limitBased
) implements DomainCommand {
}
