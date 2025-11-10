package com.acme.payments.e2e.scenario;

import com.acme.payments.domain.model.AccountType;
import com.acme.payments.domain.model.Money;
import com.acme.payments.domain.model.PeriodType;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Internal metadata for tracking generated accounts. Used by the scenario builder to maintain
 * referential integrity and generate related transactions and payments.
 */
public record AccountMetadata(
        UUID accountId,
        UUID customerId,
        String currencyCode,
        BigDecimal initialBalance,
        Map<PeriodType, Money> limits,
        AccountType accountType,
        String transitNumber,
        boolean limitBased) {
}
