package com.acme.payments.e2e.generator;

import com.github.javafaker.Faker;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Random;
import java.util.UUID;

/**
 * Base generator providing common utilities for E2E test data generation.
 * All other generators should extend this class to access Faker and utility methods.
 */
@Getter
public abstract class BaseDataGenerator {
    protected final Faker faker;
    protected final Random random;

    public BaseDataGenerator() {
        this.faker = new Faker();
        this.random = new Random();
    }

    public BaseDataGenerator(long seed) {
        this.faker = new Faker(new Random(seed));
        this.random = new Random(seed);
    }

    /**
     * Generate a random UUID
     */
    protected UUID generateId() {
        return UUID.randomUUID();
    }

    /**
     * Generate a correlation ID (UUID as string)
     */
    protected String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generate current timestamp
     */
    protected Instant generateTimestamp() {
        return Instant.now();
    }

    /**
     * Generate a future date within the next N days
     */
    protected LocalDate generateFutureDate(int maxDaysInFuture) {
        int daysOffset = random.nextInt(maxDaysInFuture + 1);
        return LocalDate.now().plusDays(daysOffset);
    }

    /**
     * Generate a skewed amount using inverse transform sampling.
     * This creates a distribution where a certain percentile is at the threshold.
     *
     * For example, if skewPercentile=0.9 and threshold=100000, then 90% of values
     * will be below 100000.
     *
     * @param min Minimum value
     * @param max Maximum value
     * @param skewPercentile The percentile at which the threshold occurs (0-1)
     * @param threshold The value at the skew percentile
     * @return A skewed random amount
     */
    protected BigDecimal generateSkewedAmount(BigDecimal min, BigDecimal max, double skewPercentile, BigDecimal threshold) {
        // Generate uniform random value between 0 and 1
        double u = random.nextDouble();

        // Calculate the shape parameter (alpha) for power law distribution
        // Using the threshold constraint to determine alpha
        double alpha = Math.log(skewPercentile) / Math.log((threshold.doubleValue() - min.doubleValue()) / (max.doubleValue() - min.doubleValue()));

        // Apply inverse transform: x = min + (max - min) * u^(1/alpha)
        double range = max.doubleValue() - min.doubleValue();
        double value = min.doubleValue() + range * Math.pow(u, 1.0 / alpha);

        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Generate a random amount within a range (uniform distribution)
     */
    protected BigDecimal generateUniformAmount(BigDecimal min, BigDecimal max) {
        double range = max.doubleValue() - min.doubleValue();
        double value = min.doubleValue() + (random.nextDouble() * range);
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Generate a random integer within a range (inclusive)
     */
    protected int generateRandomInt(int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }

    /**
     * Determine if something should happen based on a percentage (0-100)
     */
    protected boolean shouldOccur(int percentage) {
        return random.nextInt(100) < percentage;
    }

    /**
     * Select a random item from an array
     */
    protected <T> T selectRandom(T[] array) {
        return array[random.nextInt(array.length)];
    }

    /**
     * Calculate a percentage of an amount with a minimum floor
     */
    protected BigDecimal calculatePercentage(BigDecimal amount, double percentage, BigDecimal minimum) {
        BigDecimal calculated = amount.multiply(BigDecimal.valueOf(percentage / 100.0))
            .setScale(2, RoundingMode.HALF_UP);
        return calculated.max(minimum);
    }
}
