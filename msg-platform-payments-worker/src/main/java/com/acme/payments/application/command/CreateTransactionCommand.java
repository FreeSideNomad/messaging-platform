package com.acme.payments.application.command;

import com.acme.payments.domain.model.Money;
import com.acme.payments.domain.model.TransactionType;
import com.acme.reliable.command.DomainCommand;
import java.util.UUID;

/** Command to create a transaction on an account */
public record CreateTransactionCommand(
    UUID accountId, TransactionType transactionType, Money amount, String description)
    implements DomainCommand {}
