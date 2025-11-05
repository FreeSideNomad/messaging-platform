package com.acme.payments.e2e.generator;

import com.acme.payments.application.command.InitiateCreateAccountProcess;
import com.acme.payments.domain.model.AccountType;
import com.acme.payments.domain.model.Money;
import com.acme.payments.domain.model.PeriodType;

import java.math.BigDecimal;
import java.util.*;

/**
 * Generates account creation process commands with realistic distributions.
 *
 * Key features:
 * - Skewed balance distribution (90% of accounts < 100K)
 * - Automatic limit calculation based on balance
 * - Support for multiple currencies
 * - Configurable limit-based percentage
 *
 * Uses InitiateCreateAccountProcess which orchestrates the full account creation
 * including limits setup.
 */
public class AccountDataGenerator extends BaseDataGenerator {

    private static final BigDecimal MIN_BALANCE = BigDecimal.valueOf(10_000);
    private static final BigDecimal MAX_BALANCE = BigDecimal.valueOf(1_000_000);
    private static final BigDecimal SKEW_THRESHOLD = BigDecimal.valueOf(100_000);
    private static final double SKEW_PERCENTILE = 0.9;

    private static final String[] CURRENCIES = {"USD", "EUR", "GBP", "CAD", "JPY"};
    private static final String[] TRANSIT_NUMBERS = {"001", "002", "003", "004", "005"};

    public AccountDataGenerator() {
        super();
    }

    public AccountDataGenerator(long seed) {
        super(seed);
    }

    /**
     * Generate a single account creation process command
     */
    public InitiateCreateAccountProcess generateAccount(int limitBasedPercentage) {
        UUID customerId = generateCustomerId();
        String currencyCode = selectCurrency();
        BigDecimal initialBalance = calculateInitialBalance();
        boolean limitBased = isLimitBased(limitBasedPercentage);
        AccountType accountType = selectAccountType();
        String transitNumber = generateTransitNumber();

        // Calculate limits only for limit-based accounts
        // Non-limit-based accounts must have null limits per command validation
        Map<PeriodType, Money> limits = limitBased
            ? calculateLimits(initialBalance, currencyCode)
            : null;

        return new InitiateCreateAccountProcess(
            customerId,
            currencyCode,
            transitNumber,
            accountType,
            limitBased,
            limits
        );
    }

    /**
     * Generate multiple account creation process commands
     */
    public List<InitiateCreateAccountProcess> generateAccounts(int count, int limitBasedPercentage) {
        List<InitiateCreateAccountProcess> accounts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            accounts.add(generateAccount(limitBasedPercentage));
        }
        return accounts;
    }

    /**
     * Generate a random customer ID (just a UUID, no Customer entity)
     */
    protected UUID generateCustomerId() {
        return generateId();
    }

    /**
     * Calculate initial balance with skewed distribution (90% < 100K)
     */
    protected BigDecimal calculateInitialBalance() {
        return generateSkewedAmount(MIN_BALANCE, MAX_BALANCE, SKEW_PERCENTILE, SKEW_THRESHOLD);
    }

    /**
     * Calculate limits based on balance percentages with minimum floors
     */
    protected Map<PeriodType, Money> calculateLimits(BigDecimal balance, String currencyCode) {
        Map<PeriodType, Money> limits = new HashMap<>();

        // Minute: 2% of balance, min 2,000
        BigDecimal minuteLimit = calculatePercentage(balance, 2.0, BigDecimal.valueOf(2_000));
        limits.put(PeriodType.MINUTE, new Money(minuteLimit, currencyCode));

        // Hour: 10% of balance, min 10,000
        BigDecimal hourLimit = calculatePercentage(balance, 10.0, BigDecimal.valueOf(10_000));
        limits.put(PeriodType.HOUR, new Money(hourLimit, currencyCode));

        // Day: 50% of balance, min 100,000
        BigDecimal dayLimit = calculatePercentage(balance, 50.0, BigDecimal.valueOf(100_000));
        limits.put(PeriodType.DAY, new Money(dayLimit, currencyCode));

        // Week: 100% of balance, min 1,000,000
        BigDecimal weekLimit = calculatePercentage(balance, 100.0, BigDecimal.valueOf(1_000_000));
        limits.put(PeriodType.WEEK, new Money(weekLimit, currencyCode));

        // Month: 500% of balance, min 5,000,000
        BigDecimal monthLimit = calculatePercentage(balance, 500.0, BigDecimal.valueOf(5_000_000));
        limits.put(PeriodType.MONTH, new Money(monthLimit, currencyCode));

        return limits;
    }

    /**
     * Generate a transit number
     */
    protected String generateTransitNumber() {
        return selectRandom(TRANSIT_NUMBERS);
    }

    /**
     * Select an account type with weighted distribution:
     * 70% CHECKING, 20% SAVINGS, 10% others
     */
    protected AccountType selectAccountType() {
        int roll = random.nextInt(100);
        if (roll < 70) {
            return AccountType.CHECKING;
        } else if (roll < 90) {
            return AccountType.SAVINGS;
        } else if (roll < 95) {
            return AccountType.CREDIT_CARD;
        } else {
            return AccountType.LINE_OF_CREDIT;
        }
    }

    /**
     * Determine if account should be limit-based based on percentage
     */
    protected boolean isLimitBased(int limitBasedPercentage) {
        return shouldOccur(limitBasedPercentage);
    }

    /**
     * Select a currency (can be weighted if needed)
     */
    protected String selectCurrency() {
        // Currently uniform, but could weight towards USD
        return selectRandom(CURRENCIES);
    }
}
