package com.acme.payments.application.command;

import com.acme.payments.domain.model.Beneficiary;
import com.acme.payments.domain.model.Money;
import com.acme.reliable.command.DomainCommand;
import java.time.LocalDate;
import java.util.UUID;

/** Command to create a payment */
public record CreatePaymentCommand(
    UUID debitAccountId,
    Money debitAmount,
    Money creditAmount,
    LocalDate valueDate,
    Beneficiary beneficiary)
    implements DomainCommand {}
