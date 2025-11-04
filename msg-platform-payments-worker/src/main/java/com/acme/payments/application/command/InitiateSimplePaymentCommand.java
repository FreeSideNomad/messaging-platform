package com.acme.payments.application.command;

import com.acme.payments.domain.model.Beneficiary;
import com.acme.payments.domain.model.Money;
import com.acme.reliable.command.DomainCommand;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Command to initiate a simple payment process.
 * This command kicks off the payment saga orchestration.
 */
public record InitiateSimplePaymentCommand(
    UUID customerId,
    UUID debitAccountId,
    Money debitAmount,
    Money creditAmount,
    LocalDate valueDate,
    Beneficiary beneficiary,
    String description
) implements DomainCommand {

    /**
     * Check if this payment requires FX conversion
     */
    public boolean requiresFx() {
        return !debitAmount.currencyCode().equals(creditAmount.currencyCode());
    }
}
