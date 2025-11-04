package com.acme.payments.application.command;

import com.acme.payments.domain.model.Money;
import com.acme.payments.domain.model.PeriodType;
import com.acme.reliable.command.DomainCommand;

import java.util.Map;
import java.util.UUID;

/**
 * Command to create all limits for an account in one batch operation.
 * This command is typically executed as part of the CreateAccount process
 * when limitBased=true.
 */
public record CreateLimitsCommand(
    UUID accountId,
    String currencyCode,
    Map<PeriodType, Money> limits
) implements DomainCommand {

    public CreateLimitsCommand {
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new IllegalArgumentException("Currency code cannot be null or blank");
        }
        if (limits == null || limits.isEmpty()) {
            throw new IllegalArgumentException("Limits map cannot be null or empty");
        }

        // Validate all limit amounts match account currency and are positive
        limits.forEach((periodType, money) -> {
            if (money == null) {
                throw new IllegalArgumentException(
                    "Limit amount for " + periodType + " cannot be null"
                );
            }
            if (!money.currencyCode().equals(currencyCode)) {
                throw new IllegalArgumentException(
                    "All limit amounts must match account currency: " + currencyCode +
                    " (found " + money.currencyCode() + " for " + periodType + ")"
                );
            }
            if (!money.isPositive()) {
                throw new IllegalArgumentException(
                    "Limit amount for " + periodType + " must be positive"
                );
            }
        });
    }
}
