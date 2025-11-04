package com.acme.payments.application.command;

import com.acme.payments.domain.model.AccountType;
import com.acme.payments.domain.model.Money;
import com.acme.payments.domain.model.PeriodType;
import com.acme.reliable.command.DomainCommand;

import java.util.Map;
import java.util.UUID;

/**
 * Command to create a new account.
 * If limitBased=true, the limits map must contain at least one entry.
 */
public record CreateAccountCommand(
    UUID customerId,
    String currencyCode,
    String transitNumber,
    AccountType accountType,
    boolean limitBased,
    Map<PeriodType, Money> limits
) implements DomainCommand {

    public CreateAccountCommand {
        if (limitBased && (limits == null || limits.isEmpty())) {
            throw new IllegalArgumentException(
                "Limit-based accounts must have at least one limit configuration"
            );
        }
        if (!limitBased && limits != null && !limits.isEmpty()) {
            throw new IllegalArgumentException(
                "Non-limit-based accounts cannot have limit configurations"
            );
        }

        // Validate all limit amounts match account currency
        if (limits != null) {
            limits.forEach((periodType, money) -> {
                if (money != null && !money.currencyCode().equals(currencyCode)) {
                    throw new IllegalArgumentException(
                        "All limit amounts must match account currency: " + currencyCode
                    );
                }
            });
        }
    }
}
