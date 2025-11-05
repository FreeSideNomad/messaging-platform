package com.acme.payments.application.command;

import com.acme.payments.domain.model.Money;
import com.acme.reliable.command.DomainCommand;
import java.time.LocalDate;
import java.util.UUID;

/** Command to book a foreign exchange contract */
public record BookFxCommand(
    UUID customerId,
    UUID debitAccountId,
    Money debitAmount,
    Money creditAmount,
    LocalDate valueDate)
    implements DomainCommand {}
