package com.acme.payments.e2e;

import com.acme.payments.application.command.CreateTransactionCommand;
import com.acme.payments.application.command.InitiateCreateAccountProcess;
import com.acme.payments.application.command.InitiateSimplePaymentCommand;
import com.acme.payments.domain.model.PeriodType;
import com.acme.payments.e2e.generator.AccountDataGenerator;
import com.acme.payments.e2e.generator.PaymentDataGenerator;
import com.acme.payments.e2e.generator.TransactionDataGenerator;
import com.acme.payments.e2e.output.MqJsonOutputAdapter;
import com.acme.payments.e2e.output.VegetaOutputAdapter;
import com.acme.payments.e2e.scenario.E2ETestScenario;
import com.acme.payments.e2e.scenario.E2ETestScenarioBuilder;
import com.acme.payments.e2e.scenario.TestScenarioConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for E2E testing framework
 */
@DisplayName("E2E Framework Tests")
class E2EFrameworkTest {

    @Test
    @DisplayName("AccountDataGenerator should generate valid accounts with correct limit distribution")
    void testAccountDataGenerator() {
        AccountDataGenerator generator = new AccountDataGenerator(42L); // Fixed seed for reproducibility
        List<InitiateCreateAccountProcess> accounts = generator.generateAccounts(100, 30);

        assertThat(accounts).hasSize(100);

        // Verify all accounts have required fields
        for (InitiateCreateAccountProcess account : accounts) {
            assertThat(account.customerId()).isNotNull();
            assertThat(account.currencyCode()).isNotEmpty();
            assertThat(account.transitNumber()).isNotEmpty();
            assertThat(account.accountType()).isNotNull();

            // Only limit-based accounts should have limits
            if (account.limitBased()) {
                assertThat(account.limits()).isNotEmpty();
                assertThat(account.limits()).containsKeys(
                    PeriodType.MINUTE,
                    PeriodType.HOUR,
                    PeriodType.DAY,
                    PeriodType.WEEK,
                    PeriodType.MONTH
                );
            } else {
                assertThat(account.limits()).isNull();
            }
        }

        // Verify limit-based percentage (should be around 30%)
        long limitBasedCount = accounts.stream().filter(InitiateCreateAccountProcess::limitBased).count();
        double percentage = (limitBasedCount * 100.0) / accounts.size();
        assertThat(percentage).isBetween(20.0, 40.0); // Allow some variance
    }

    @Test
    @DisplayName("TransactionDataGenerator should generate valid transactions")
    void testTransactionDataGenerator() {
        TransactionDataGenerator generator = new TransactionDataGenerator();
        UUID accountId = UUID.randomUUID();

        // Test opening credit
        var openingTxn = generator.generateOpeningCredit(
            accountId,
            new com.acme.payments.domain.model.Money(BigDecimal.valueOf(10000), "USD")
        );
        assertThat(openingTxn.accountId()).isEqualTo(accountId);
        assertThat(openingTxn.transactionType()).isEqualTo(com.acme.payments.domain.model.TransactionType.CREDIT);
        assertThat(openingTxn.amount().amount()).isEqualByComparingTo(BigDecimal.valueOf(10000));

        // Test funding transactions
        List<CreateTransactionCommand> funding = generator.generateFundingTransactions(
            accountId,
            "USD",
            BigDecimal.valueOf(50000),
            5, 10
        );
        assertThat(funding).hasSizeBetween(5, 10);
        assertThat(funding).allMatch(txn -> txn.accountId().equals(accountId));
        assertThat(funding).allMatch(txn -> txn.transactionType() == com.acme.payments.domain.model.TransactionType.CREDIT);
    }

    @Test
    @DisplayName("PaymentDataGenerator should generate valid payments")
    void testPaymentDataGenerator() {
        PaymentDataGenerator generator = new PaymentDataGenerator();
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        // Create simple limits for testing
        var limits = java.util.Map.of(
            PeriodType.HOUR, new com.acme.payments.domain.model.Money(BigDecimal.valueOf(10000), "USD")
        );

        List<InitiateSimplePaymentCommand> payments = generator.generatePayments(
            customerId, accountId, "USD", limits, 10, 20, 25
        );

        assertThat(payments).hasSizeBetween(10, 20);
        assertThat(payments).allMatch(p -> p.customerId().equals(customerId));
        assertThat(payments).allMatch(p -> p.debitAccountId().equals(accountId));
        assertThat(payments).allMatch(p -> p.beneficiary() != null);
        assertThat(payments).allMatch(p -> p.valueDate() != null);

        // Check that some are FX payments (approximately 25%)
        long fxCount = payments.stream()
            .filter(p -> !p.debitAmount().currencyCode().equals(p.creditAmount().currencyCode()))
            .count();
        assertThat(fxCount).isGreaterThan(0); // At least some FX payments
    }

    @Test
    @DisplayName("E2ETestScenarioBuilder should generate complete scenario")
    void testE2ETestScenarioBuilder() {
        E2ETestScenarioBuilder builder = new E2ETestScenarioBuilder(42L); // Fixed seed
        TestScenarioConfig config = TestScenarioConfig.smoke("./test-output");

        E2ETestScenario scenario = builder.build(config);

        assertThat(scenario.getAccountCommands()).hasSize(10);
        assertThat(scenario.getOpeningTransactions()).hasSize(10);
        assertThat(scenario.getFundingTransactions()).hasSizeGreaterThanOrEqualTo(20); // min 2 per account
        assertThat(scenario.getPaymentCommands()).hasSizeGreaterThanOrEqualTo(50); // min 5 per account

        // Verify metrics
        assertThat(scenario.getMetrics().accountCount()).isEqualTo(10);
        assertThat(scenario.getMetrics().totalCommands()).isGreaterThan(80);

        // Verify account index
        assertThat(scenario.getAccountIndex()).hasSize(10);
    }

    @Test
    @DisplayName("VegetaOutputAdapter should write target files")
    void testVegetaOutputAdapter(@TempDir Path tempDir) throws IOException {
        E2ETestScenarioBuilder builder = new E2ETestScenarioBuilder(42L);
        E2ETestScenario scenario = builder.build(TestScenarioConfig.smoke(tempDir.toString()));

        VegetaOutputAdapter adapter = new VegetaOutputAdapter();
        adapter.writeSequencedTargets(scenario, tempDir.toString());

        // Verify files were created
        Path vegetaDir = tempDir.resolve("vegeta");
        assertThat(vegetaDir).exists();
        assertThat(vegetaDir.resolve("01-accounts.txt")).exists();
        assertThat(vegetaDir.resolve("02-opening-credits.txt")).exists();
        assertThat(vegetaDir.resolve("03-funding-txns.txt")).exists();
        assertThat(vegetaDir.resolve("04-payments.txt")).exists();

        // Verify content format
        List<String> lines = Files.readAllLines(vegetaDir.resolve("01-accounts.txt"));
        assertThat(lines).isNotEmpty();
        assertThat(lines.get(0)).startsWith("POST http://localhost:8080/api/accounts");
    }

    @Test
    @DisplayName("MqJsonOutputAdapter should write message files")
    void testMqJsonOutputAdapter(@TempDir Path tempDir) throws IOException {
        E2ETestScenarioBuilder builder = new E2ETestScenarioBuilder(42L);
        E2ETestScenario scenario = builder.build(TestScenarioConfig.smoke(tempDir.toString()));

        MqJsonOutputAdapter adapter = new MqJsonOutputAdapter();
        adapter.writeAllMessages(scenario, tempDir.toString());

        // Verify files were created
        Path mqDir = tempDir.resolve("mq");
        assertThat(mqDir).exists();
        assertThat(mqDir.resolve("accounts.jsonl")).exists();
        assertThat(mqDir.resolve("opening-credits.jsonl")).exists();
        assertThat(mqDir.resolve("funding-txns.jsonl")).exists();
        assertThat(mqDir.resolve("payments.jsonl")).exists();
        assertThat(mqDir.resolve("all-messages.jsonl")).exists();

        // Verify content format
        List<String> lines = Files.readAllLines(mqDir.resolve("accounts.jsonl"));
        assertThat(lines).isNotEmpty();
        assertThat(lines.get(0)).contains("messageId");
        assertThat(lines.get(0)).contains("commandType");
        assertThat(lines.get(0)).contains("payload");
    }
}
