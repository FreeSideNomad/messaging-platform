package com.acme.payments.application.command;

import com.acme.reliable.command.DomainCommand;
import java.util.UUID;

/**
 * Compensation command to reverse a previously created transaction Creates a new transaction with
 * the opposite amount
 */
public record ReverseTransactionCommand(UUID transactionId, String reason)
    implements DomainCommand {}
