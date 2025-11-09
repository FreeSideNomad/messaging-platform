package com.acme.payments.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.payments.application.command.CreateAccountCommand;
import com.acme.payments.application.command.InitiateCreateAccountProcess;
import com.acme.payments.domain.model.AccountType;
import com.acme.payments.domain.model.Money;
import com.acme.payments.domain.model.PeriodType;
import com.acme.payments.integration.PaymentsIntegrationTestBase;
import com.acme.reliable.core.Jsons;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration test for CreateAccount process with limit creation. Tests the process definition and
 * command flow.
 *
 * This test class extends PaymentsIntegrationTestBase which provides:
 * - H2 in-memory database with Flyway migrations
 * - Hardwired service and repository instances (no @MicronautTest DI required)
 */
class CreateAccountProcessWithLimitsIntegrationTest extends PaymentsIntegrationTestBase {

  private CreateAccountProcessDefinition processDefinition;

  @BeforeEach
  void setUp() throws Exception {
    // Setup Micronaut ApplicationContext with DI and AOP
    // Database setup (H2 with Flyway) is handled automatically by setupDatabaseForTest() @BeforeEach
    // in PaymentsIntegrationTestBase
    super.setupContext();

    // Manually create the process definition (no DI needed)
    processDefinition = new CreateAccountProcessDefinition();
  }

  @org.junit.jupiter.api.AfterEach
  void tearDown() throws Exception {
    super.tearDownContext();
  }

  @Test
  @DisplayName("Process definition should have correct type")
  void testProcessType() {
    assertThat(processDefinition.getProcessType()).isEqualTo("InitiateCreateAccountProcess");
  }

  @Test
  @DisplayName("Should initialize process state from InitiateCreateAccountProcess without limits")
  void testProcessInitialization_NoLimits() {
    // Given: Initiate create account process command without limits
    UUID customerId = UUID.randomUUID();
    InitiateCreateAccountProcess cmd =
        new InitiateCreateAccountProcess(
            customerId, "USD", "001", AccountType.CHECKING, false, null);

    // When: Initializing process
    Map<String, Object> processState = processDefinition.initializeProcessState(cmd);

    // Then: Process state should contain all command data
    assertThat(processState).isNotNull();
    assertThat(processState.get("customerId")).isEqualTo(customerId.toString());
    assertThat(processState.get("currencyCode")).isEqualTo("USD");
    assertThat(processState.get("transitNumber")).isEqualTo("001");
    assertThat(processState.get("accountType")).isEqualTo("CHECKING");
    assertThat(processState.get("limitBased")).isEqualTo(false);
    assertThat(processState.get("limits")).isNull();
  }

  @Test
  @DisplayName("Should initialize process state from InitiateCreateAccountProcess with limits")
  void testProcessInitialization_WithLimits() {
    // Given: Initiate create account process command with limits
    UUID customerId = UUID.randomUUID();
    Map<PeriodType, Money> limits =
        Map.of(
            PeriodType.HOUR, Money.of(1000, "USD"),
            PeriodType.DAY, Money.of(5000, "USD"));

    InitiateCreateAccountProcess cmd =
        new InitiateCreateAccountProcess(
            customerId, "USD", "001", AccountType.CHECKING, true, limits);

    // When: Initializing process
    Map<String, Object> processState = processDefinition.initializeProcessState(cmd);

    // Then: Process state should contain limits
    assertThat(processState).isNotNull();
    assertThat(processState.get("limitBased")).isEqualTo(true);
    assertThat(processState.get("limits")).isNotNull();

    // Verify limits can be serialized
    String json = Jsons.toJson(processState);
    assertThat(json).isNotEmpty();
    assertThat(json).contains("limitBased");
    assertThat(json).contains("limits");
  }

  @Test
  @DisplayName("Should handle account creation without limits")
  void testAccountCreation_NoLimits() {
    // Given: Create account command without limits
    UUID customerId = UUID.randomUUID();
    CreateAccountCommand cmd =
        new CreateAccountCommand(customerId, "USD", "001", AccountType.CHECKING, false);

    // When: Creating account
    Map<String, Object> result = accountService.handleCreateAccount(cmd);

    // Then: Account should be created and return accountId
    assertThat(result).isNotNull();
    assertThat(result.get("accountId")).isNotNull();
    assertThat(result.get("accountNumber")).asString().startsWith("ACC");

    // Verify account exists in repository
    UUID accountId = UUID.fromString((String) result.get("accountId"));
    assertThat(accountRepository.findById(accountId)).isPresent();
  }

  @Test
  @DisplayName("Should handle limit creation after account creation")
  void testLimitCreation_AfterAccountCreation() {
    // Given: Account has been created
    UUID customerId = UUID.randomUUID();
    CreateAccountCommand accountCmd =
        new CreateAccountCommand(customerId, "USD", "001", AccountType.CHECKING, true);

    Map<String, Object> accountResult = accountService.handleCreateAccount(accountCmd);
    UUID accountId = UUID.fromString((String) accountResult.get("accountId"));

    // When: Creating limits
    Map<PeriodType, Money> limits =
        Map.of(
            PeriodType.HOUR, Money.of(1000, "USD"),
            PeriodType.DAY, Money.of(5000, "USD"));

    var createLimitsCmd =
        new com.acme.payments.application.command.CreateLimitsCommand(accountId, "USD", limits);

    Map<String, Object> limitsResult = limitService.handleCreateLimits(createLimitsCmd);

    // Then: Limits should be created
    assertThat(limitsResult).isNotNull();
    assertThat(limitsResult.get("limitCount")).isEqualTo(2);

    // Verify limits exist in repository
    var savedLimits = accountLimitRepository.findActiveByAccountId(accountId);
    assertThat(savedLimits).hasSize(2);
  }

  @Test
  @DisplayName("Process graph should conditionally execute CreateLimits")
  void testProcessGraphConditionalExecution() {
    // Given: Process graph
    var graph = processDefinition.defineProcess();

    // Then: Should have InitiateCreateAccountProcess as initial step
    assertThat(graph.getInitialStep()).isEqualTo("InitiateCreateAccountProcess");

    // Next step after initiation should be CreateAccount
    var nextStep = graph.getNextStep("InitiateCreateAccountProcess", Map.of());
    assertThat(nextStep).isPresent();
    assertThat(nextStep.get()).isEqualTo("CreateAccount");

    // When: limitBased=true
    Map<String, Object> dataWithLimits =
        Map.of("limitBased", true, "limits", Map.of(PeriodType.DAY, Money.of(1000, "USD")));

    var nextStepWithLimits = graph.getNextStep("CreateAccount", dataWithLimits);
    assertThat(nextStepWithLimits).isPresent();
    assertThat(nextStepWithLimits.get()).isEqualTo("CreateLimits");

    // When: limitBased=false
    Map<String, Object> dataWithoutLimits = new HashMap<>();
    dataWithoutLimits.put("limitBased", false);
    dataWithoutLimits.put("limits", null);

    var nextStepWithoutLimits = graph.getNextStep("CreateAccount", dataWithoutLimits);
    assertThat(nextStepWithoutLimits).isPresent();
    assertThat(nextStepWithoutLimits.get()).isEqualTo("CompleteAccountCreation");
  }

  @Test
  @DisplayName("Should be retryable on transient errors")
  void testRetryability() {
    // Given: Process definition

    // Then: Should be retryable on transient errors
    assertThat(processDefinition.isRetryable("CreateAccount", "connection timeout")).isTrue();
    assertThat(processDefinition.isRetryable("CreateLimits", "database connection failed"))
        .isTrue();
    assertThat(processDefinition.isRetryable("CreateAccount", "temporary error")).isTrue();
    assertThat(processDefinition.isRetryable("CreateLimits", "deadlock detected")).isTrue();

    // And: Should not retry on non-transient errors
    assertThat(processDefinition.isRetryable("CreateAccount", "invalid data")).isFalse();
    assertThat(processDefinition.isRetryable("CreateLimits", "validation failed")).isFalse();
  }

  @Test
  @DisplayName("Should have max retries configured")
  void testMaxRetries() {
    // Given: Process definition

    // Then: Should allow 3 retries
    assertThat(processDefinition.getMaxRetries("CreateAccount")).isEqualTo(3);
    assertThat(processDefinition.getMaxRetries("CreateLimits")).isEqualTo(3);
  }

  @Test
  @DisplayName("Limits should be aligned to time buckets")
  void testTimeBucketAlignment() {
    // Given: Account created
    UUID customerId = UUID.randomUUID();
    CreateAccountCommand accountCmd =
        new CreateAccountCommand(customerId, "USD", "001", AccountType.CHECKING, true);

    Map<String, Object> accountResult = accountService.handleCreateAccount(accountCmd);
    UUID accountId = UUID.fromString((String) accountResult.get("accountId"));

    // When: Creating hour limit
    var createLimitsCmd =
        new com.acme.payments.application.command.CreateLimitsCommand(
            accountId, "USD", Map.of(PeriodType.HOUR, Money.of(1000, "USD")));

    limitService.handleCreateLimits(createLimitsCmd);

    // Then: Limit should be aligned to hour boundary
    var savedLimits = accountLimitRepository.findActiveByAccountId(accountId);
    assertThat(savedLimits).hasSize(1);

    var limit = savedLimits.get(0);
    var startTime = limit.getStartTime();

    // Start time should have zero minutes and seconds
    assertThat(startTime.toString()).matches(".*T\\d{2}:00:00.*");
  }
}
