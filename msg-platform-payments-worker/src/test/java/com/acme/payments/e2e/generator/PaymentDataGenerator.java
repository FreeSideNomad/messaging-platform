package com.acme.payments.e2e.generator;

import com.acme.payments.application.command.InitiateSimplePaymentCommand;
import com.acme.payments.domain.model.Beneficiary;
import com.acme.payments.domain.model.Money;
import com.acme.payments.domain.model.PeriodType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generates payment commands with realistic distributions.
 *
 * <p>Payment amount distribution (based on hourly limit): - 50% of payments: < 10% of hourly limit
 * - 30% of payments: 10-50% of hourly limit - 15% of payments: 50-100% of hourly limit - 5% of
 * payments: > hourly limit (to test limit violations)
 */
public class PaymentDataGenerator extends BaseDataGenerator {

    private static final String[] BANK_NAMES = {
            "First National Bank",
            "Global Trust Bank",
            "Metropolitan Bank",
            "City Savings Bank",
            "United Credit Bank"
    };

    private static final String[] TRANSIT_NUMBERS = {
            "001", "002", "003", "004", "005", "006", "007", "008", "009", "010"
    };

    public PaymentDataGenerator() {
        super();
    }

    public PaymentDataGenerator(long seed) {
        super(seed);
    }

    /**
     * Generate a single payment command (same currency)
     */
    public InitiateSimplePaymentCommand generatePayment(
            UUID customerId, UUID debitAccountId, String currencyCode, Map<PeriodType, Money> limits) {
        Money amount = generatePaymentAmount(currencyCode, limits, false);
        LocalDate valueDate = generateFutureDate(5); // 0-5 days in future
        Beneficiary beneficiary = generateBeneficiary();
        String description = "Payment for " + faker.commerce().productName();

        return new InitiateSimplePaymentCommand(
                customerId,
                debitAccountId,
                amount, // debitAmount
                amount, // creditAmount (same currency)
                valueDate,
                beneficiary,
                description);
    }

    /**
     * Generate an FX payment (cross-currency)
     */
    public InitiateSimplePaymentCommand generateFxPayment(
            UUID customerId,
            UUID debitAccountId,
            String debitCurrency,
            String creditCurrency,
            Map<PeriodType, Money> limits) {
        Money debitAmount = generatePaymentAmount(debitCurrency, limits, false);

        // Apply a random FX rate (between 0.5 and 2.0 for variety)
        double fxRate = 0.5 + (random.nextDouble() * 1.5);
        BigDecimal creditAmountValue =
                debitAmount
                        .amount()
                        .multiply(BigDecimal.valueOf(fxRate))
                        .setScale(2, java.math.RoundingMode.HALF_UP);
        Money creditAmount = new Money(creditAmountValue, creditCurrency);

        LocalDate valueDate = generateFutureDate(5);
        Beneficiary beneficiary = generateBeneficiary();
        String description = "FX Payment for " + faker.commerce().productName();

        return new InitiateSimplePaymentCommand(
                customerId, debitAccountId, debitAmount, creditAmount, valueDate, beneficiary, description);
    }

    /**
     * Generate multiple payments for an account
     */
    public List<InitiateSimplePaymentCommand> generatePayments(
            UUID customerId,
            UUID accountId,
            String currencyCode,
            Map<PeriodType, Money> limits,
            int minCount,
            int maxCount,
            int fxPercentage) {
        int count = generateRandomInt(minCount, maxCount);
        List<InitiateSimplePaymentCommand> payments = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            if (shouldOccur(fxPercentage)) {
                // Generate FX payment
                String creditCurrency = selectDifferentCurrency(currencyCode);
                payments.add(
                        generateFxPayment(customerId, accountId, currencyCode, creditCurrency, limits));
            } else {
                // Generate same-currency payment
                payments.add(generatePayment(customerId, accountId, currencyCode, limits));
            }
        }

        return payments;
    }

    /**
     * Generate a beneficiary with realistic data
     */
    public Beneficiary generateBeneficiary() {
        String name = faker.name().fullName();
        String accountNumber = "ACC" + faker.number().digits(10);
        String transitNumber = selectRandom(TRANSIT_NUMBERS);
        String bankName = selectRandom(BANK_NAMES);

        return new Beneficiary(name, accountNumber, transitNumber, bankName);
    }

    /**
     * Generate payment amount with realistic distribution relative to hourly limit
     *
     * @param enableLimitViolations If true, 5% of payments will exceed hourly limit
     */
    protected Money generatePaymentAmount(
            String currencyCode, Map<PeriodType, Money> limits, boolean enableLimitViolations) {
        // For non-limit-based accounts (limits=null), use default amounts
        BigDecimal limitAmount;
        if (limits != null && limits.containsKey(PeriodType.HOUR)) {
            Money hourlyLimit = limits.get(PeriodType.HOUR);
            limitAmount = hourlyLimit.amount();
        } else {
            // Default: use 5,000 as reference amount for non-limit-based accounts
            limitAmount = BigDecimal.valueOf(5_000);
        }

        int roll = random.nextInt(100);
        BigDecimal amount;

        if (roll < 50) {
            // 50%: Small payment (< 10% of hourly limit)
            BigDecimal max = limitAmount.multiply(BigDecimal.valueOf(0.10));
            amount = generateUniformAmount(BigDecimal.valueOf(10), max);
        } else if (roll < 80) {
            // 30%: Medium payment (10-50% of hourly limit)
            BigDecimal min = limitAmount.multiply(BigDecimal.valueOf(0.10));
            BigDecimal max = limitAmount.multiply(BigDecimal.valueOf(0.50));
            amount = generateUniformAmount(min, max);
        } else if (roll < 95) {
            // 15%: Large payment (50-100% of hourly limit)
            BigDecimal min = limitAmount.multiply(BigDecimal.valueOf(0.50));
            amount = generateUniformAmount(min, limitAmount);
        } else if (enableLimitViolations) {
            // 5%: Over-limit payment (100-150% of hourly limit) - for testing violations
            BigDecimal max = limitAmount.multiply(BigDecimal.valueOf(1.50));
            amount = generateUniformAmount(limitAmount, max);
        } else {
            // If violations disabled, make it just under the limit
            amount = limitAmount.multiply(BigDecimal.valueOf(0.95));
        }

        return new Money(amount, currencyCode);
    }

    /**
     * Select a different currency from the given one
     */
    private String selectDifferentCurrency(String currentCurrency) {
        String[] currencies = {"USD", "EUR", "GBP", "CAD", "JPY"};
        List<String> available = new ArrayList<>();
        for (String currency : currencies) {
            if (!currency.equals(currentCurrency)) {
                available.add(currency);
            }
        }
        return available.get(random.nextInt(available.size()));
    }
}
