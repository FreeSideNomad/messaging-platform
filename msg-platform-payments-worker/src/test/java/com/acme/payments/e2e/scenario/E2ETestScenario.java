package com.acme.payments.e2e.scenario;

import com.acme.payments.application.command.CreateTransactionCommand;
import com.acme.payments.application.command.InitiateCreateAccountProcess;
import com.acme.payments.application.command.InitiateSimplePaymentCommand;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Complete E2E test scenario containing all generated commands and metadata for validation and
 * output generation.
 */
@Getter
public class E2ETestScenario {
    private final List<InitiateCreateAccountProcess> accountCommands;
    private final List<CreateTransactionCommand> openingTransactions;
    private final List<CreateTransactionCommand> fundingTransactions;
    private final List<InitiateSimplePaymentCommand> paymentCommands;
    private final Map<UUID, AccountMetadata> accountIndex;
    private final TestScenarioMetrics metrics;

    public E2ETestScenario(
            List<InitiateCreateAccountProcess> accountCommands,
            List<CreateTransactionCommand> openingTransactions,
            List<CreateTransactionCommand> fundingTransactions,
            List<InitiateSimplePaymentCommand> paymentCommands,
            Map<UUID, AccountMetadata> accountIndex) {
        this.accountCommands = accountCommands;
        this.openingTransactions = openingTransactions;
        this.fundingTransactions = fundingTransactions;
        this.paymentCommands = paymentCommands;
        this.accountIndex = accountIndex;
        this.metrics = calculateMetrics();
    }

    private TestScenarioMetrics calculateMetrics() {
        int totalCommands =
                accountCommands.size()
                        + openingTransactions.size()
                        + fundingTransactions.size()
                        + paymentCommands.size();

        long fxPaymentCount =
                paymentCommands.stream()
                        .filter(p -> !p.debitAmount().currencyCode().equals(p.creditAmount().currencyCode()))
                        .count();

        return new TestScenarioMetrics(
                accountCommands.size(),
                openingTransactions.size(),
                fundingTransactions.size(),
                paymentCommands.size(),
                (int) fxPaymentCount,
                totalCommands);
    }

    /**
     * Get account metadata by account ID
     */
    public AccountMetadata getAccountMetadata(UUID accountId) {
        return accountIndex.get(accountId);
    }
}
