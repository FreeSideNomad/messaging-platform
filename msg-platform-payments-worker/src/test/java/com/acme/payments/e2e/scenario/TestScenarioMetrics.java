package com.acme.payments.e2e.scenario;

/** Metrics about the generated test scenario */
public record TestScenarioMetrics(
    int accountCount,
    int openingTransactionCount,
    int fundingTransactionCount,
    int paymentCount,
    int fxPaymentCount,
    int totalCommands) {
  @Override
  public String toString() {
    return String.format(
        "Test Scenario Metrics:%n"
            + "  Accounts: %d%n"
            + "  Opening Transactions: %d%n"
            + "  Funding Transactions: %d%n"
            + "  Payments: %d (FX: %d)%n"
            + "  Total Commands: %d",
        accountCount,
        openingTransactionCount,
        fundingTransactionCount,
        paymentCount,
        fxPaymentCount,
        totalCommands);
  }
}
