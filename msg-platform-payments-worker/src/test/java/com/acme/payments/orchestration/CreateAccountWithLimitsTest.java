package com.acme.payments.orchestration;

import com.acme.payments.application.command.CreateAccountCommand;
import com.acme.payments.domain.model.AccountType;
import com.acme.payments.domain.model.Money;
import com.acme.payments.domain.model.PeriodType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CreateAccountCommand with limits
 */
class CreateAccountWithLimitsTest {

    @Test
    void testCreateAccountCommand_WithLimits_Success() {
        // Given: A create account command with limits
        UUID customerId = UUID.randomUUID();
        Map<PeriodType, Money> limits = Map.of(
            PeriodType.HOUR, Money.of(BigDecimal.valueOf(1000.00), "USD"),
            PeriodType.DAY, Money.of(BigDecimal.valueOf(5000.00), "USD"),
            PeriodType.MONTH, Money.of(BigDecimal.valueOf(50000.00), "USD")
        );

        // When: Creating the command
        CreateAccountCommand cmd = new CreateAccountCommand(
            customerId,
            "USD",
            "12345",
            AccountType.CHECKING,
            true,
            limits
        );

        // Then: Command is created successfully
        assertNotNull(cmd);
        assertEquals(customerId, cmd.customerId());
        assertEquals("USD", cmd.currencyCode());
        assertTrue(cmd.limitBased());
        assertEquals(3, cmd.limits().size());
    }

    @Test
    void testCreateAccountCommand_LimitBased_NoLimits_ThrowsException() {
        // Given: A limit-based account without limits
        UUID customerId = UUID.randomUUID();

        // When/Then: Creating the command should throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            new CreateAccountCommand(
                customerId,
                "USD",
                "12345",
                AccountType.CHECKING,
                true,
                null // No limits provided
            );
        });
    }

    @Test
    void testCreateAccountCommand_NotLimitBased_WithLimits_ThrowsException() {
        // Given: A non-limit-based account with limits
        UUID customerId = UUID.randomUUID();
        Map<PeriodType, Money> limits = Map.of(
            PeriodType.HOUR, Money.of(BigDecimal.valueOf(1000.00), "USD")
        );

        // When/Then: Creating the command should throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            new CreateAccountCommand(
                customerId,
                "USD",
                "12345",
                AccountType.CHECKING,
                false, // Not limit-based
                limits // But limits provided
            );
        });
    }

    @Test
    void testCreateAccountCommand_NotLimitBased_NoLimits_Success() {
        // Given: A non-limit-based account without limits
        UUID customerId = UUID.randomUUID();

        // When: Creating the command
        CreateAccountCommand cmd = new CreateAccountCommand(
            customerId,
            "USD",
            "12345",
            AccountType.CHECKING,
            false,
            null
        );

        // Then: Command is created successfully
        assertNotNull(cmd);
        assertFalse(cmd.limitBased());
        assertNull(cmd.limits());
    }

    @Test
    void testCreateAccountCommand_MismatchedCurrency_ThrowsException() {
        // Given: Limits with different currency than account
        UUID customerId = UUID.randomUUID();
        Map<PeriodType, Money> limits = Map.of(
            PeriodType.HOUR, Money.of(BigDecimal.valueOf(1000.00), "EUR") // EUR instead of USD
        );

        // When/Then: Creating the command should throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            new CreateAccountCommand(
                customerId,
                "USD", // USD currency
                "12345",
                AccountType.CHECKING,
                true,
                limits
            );
        });
    }
}
