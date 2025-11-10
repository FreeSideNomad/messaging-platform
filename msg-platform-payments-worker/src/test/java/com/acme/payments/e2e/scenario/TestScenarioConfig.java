package com.acme.payments.e2e.scenario;

import java.util.List;

/**
 * Configuration for E2E test scenarios. Defines all parameters for test data generation.
 */
public record TestScenarioConfig(
        int accountCount,
        int minPaymentsPerAccount,
        int maxPaymentsPerAccount,
        int minFundingPerAccount,
        int maxFundingPerAccount,
        List<String> currencies,
        int fxPaymentPercentage,
        int limitBasedAccountPercentage,
        boolean enableLimitViolations,
        String outputDirectory) {
    /**
     * Smoke test: minimal accounts for quick validation
     */
    public static TestScenarioConfig smoke(String outputDirectory) {
        return new TestScenarioConfig(
                10, // 10 accounts
                5,
                10, // 5-10 payments
                2,
                5, // 2-5 funding txns
                List.of("USD", "EUR"), // 2 currencies
                10, // 10% FX payments
                50, // 50% limit-based
                false, // No limit violations
                outputDirectory);
    }

    /**
     * Small test: suitable for development testing
     */
    public static TestScenarioConfig small(String outputDirectory) {
        return new TestScenarioConfig(
                100, 10, 20, 5, 15, List.of("USD", "EUR", "GBP"), 20, 30, true, outputDirectory);
    }

    /**
     * Medium test: moderate load testing
     */
    public static TestScenarioConfig medium(String outputDirectory) {
        return new TestScenarioConfig(
                1_000, 20, 50, 10, 30, List.of("USD", "EUR", "GBP", "CAD"), 25, 20, true, outputDirectory);
    }

    /**
     * Large test: production-like load
     */
    public static TestScenarioConfig large(String outputDirectory) {
        return new TestScenarioConfig(
                10_000,
                50,
                100,
                20,
                50,
                List.of("USD", "EUR", "GBP", "CAD", "JPY"),
                30,
                20,
                true,
                outputDirectory);
    }

    /**
     * Stress test: maximum load with high limit-based percentage
     */
    public static TestScenarioConfig stress(String outputDirectory) {
        return new TestScenarioConfig(
                100_000,
                100,
                200,
                50,
                100,
                List.of("USD", "EUR", "GBP", "CAD", "JPY"),
                40,
                80,
                true,
                outputDirectory);
    }
}
