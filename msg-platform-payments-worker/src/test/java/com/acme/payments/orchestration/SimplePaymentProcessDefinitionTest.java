package com.acme.payments.orchestration;

import static org.assertj.core.api.Assertions.*;

import com.acme.payments.application.command.*;
import com.acme.payments.domain.model.Beneficiary;
import com.acme.payments.domain.model.Money;
import com.acme.reliable.process.ProcessGraph;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for SimplePaymentProcessDefinition. Validates the process graph structure, flow, and
 * compensation logic.
 */
@DisplayName("SimplePaymentProcessDefinition Tests")
class SimplePaymentProcessDefinitionTest {

  private SimplePaymentProcessDefinition processDefinition;
  private ProcessGraph processGraph;

  @BeforeEach
  void setUp() {
    processDefinition = new SimplePaymentProcessDefinition();
    processGraph = processDefinition.defineProcess();
  }

  @Test
  @DisplayName("getProcessType - should return SimplePayment")
  void testGetProcessType() {
    assertThat(processDefinition.getProcessType()).isEqualTo("SimplePayment");
  }

  @Test
  @DisplayName("initializeProcessState - should initialize process state without FX")
  void testHandleInitiateSimplePayment_NoFx() {
    // Given
    InitiateSimplePaymentCommand cmd =
        new InitiateSimplePaymentCommand(
            UUID.randomUUID(),
            UUID.randomUUID(),
            Money.of(100, "USD"),
            Money.of(100, "USD"), // Same currency
            LocalDate.now(),
            new Beneficiary("John Doe", "123456", "001", "Test Bank"),
            "Test payment");

    // When
    Map<String, Object> state = processDefinition.initializeProcessState(cmd);

    // Then
    assertThat(state).isNotNull();
    assertThat(state).containsKey("customerId");
    assertThat(state).containsKey("debitAccountId");
    assertThat(state).containsKey("debitAmount");
    assertThat(state).containsKey("creditAmount");
    assertThat(state).containsKey("requiresFx");
    assertThat(state.get("requiresFx")).isEqualTo(false);
  }

  @Test
  @DisplayName("initializeProcessState - should initialize process state with FX")
  void testHandleInitiateSimplePayment_WithFx() {
    // Given
    InitiateSimplePaymentCommand cmd =
        new InitiateSimplePaymentCommand(
            UUID.randomUUID(),
            UUID.randomUUID(),
            Money.of(100, "USD"),
            Money.of(85, "EUR"), // Different currency
            LocalDate.now(),
            new Beneficiary("John Doe", "123456", "001", "Test Bank"),
            "FX payment");

    // When
    Map<String, Object> state = processDefinition.initializeProcessState(cmd);

    // Then
    assertThat(state).containsKey("requiresFx");
    assertThat(state.get("requiresFx")).isEqualTo(true);
  }

  @Test
  @DisplayName("defineProcess - should start with BookLimits")
  void testDefineProcess_InitialStep() {
    // Then - Step names are simplified (no "Command" suffix)
    assertThat(processGraph.getInitialStep()).isEqualTo("BookLimits");
  }

  @Test
  @DisplayName("defineProcess - BookLimits should have ReverseLimits compensation")
  void testDefineProcess_BookLimitsCompensation() {
    // When
    boolean requiresCompensation = processGraph.requiresCompensation("BookLimits");
    Optional<String> compensationStep = processGraph.getCompensationStep("BookLimits");

    // Then
    assertThat(requiresCompensation).isTrue();
    assertThat(compensationStep).isPresent();
    assertThat(compensationStep.get()).isEqualTo("ReverseLimits");
  }

  @Test
  @DisplayName("defineProcess - should navigate to BookFx when requiresFx is true")
  void testDefineProcess_NavigateToFxPath() {
    // Given
    Map<String, Object> stateWithFx = new HashMap<>();
    stateWithFx.put("requiresFx", true);

    // When
    Optional<String> nextStep = processGraph.getNextStep("BookLimits", stateWithFx);

    // Then
    assertThat(nextStep).isPresent();
    assertThat(nextStep.get()).isEqualTo("BookFx");
  }

  @Test
  @DisplayName("defineProcess - should skip BookFx when requiresFx is false")
  void testDefineProcess_SkipFxPath() {
    // Given
    Map<String, Object> stateNoFx = new HashMap<>();
    stateNoFx.put("requiresFx", false);

    // When
    Optional<String> nextStep = processGraph.getNextStep("BookLimits", stateNoFx);

    // Then
    assertThat(nextStep).isPresent();
    assertThat(nextStep.get()).isEqualTo("CreateTransaction");
  }

  @Test
  @DisplayName("defineProcess - BookFx should have UnwindFx compensation")
  void testDefineProcess_BookFxCompensation() {
    // When
    boolean requiresCompensation = processGraph.requiresCompensation("BookFx");
    Optional<String> compensationStep = processGraph.getCompensationStep("BookFx");

    // Then
    assertThat(requiresCompensation).isTrue();
    assertThat(compensationStep).isPresent();
    assertThat(compensationStep.get()).isEqualTo("UnwindFx");
  }

  @Test
  @DisplayName("defineProcess - CreateTransaction should have ReverseTransaction compensation")
  void testDefineProcess_CreateTransactionCompensation() {
    // When
    boolean requiresCompensation = processGraph.requiresCompensation("CreateTransaction");
    Optional<String> compensationStep = processGraph.getCompensationStep("CreateTransaction");

    // Then
    assertThat(requiresCompensation).isTrue();
    assertThat(compensationStep).isPresent();
    assertThat(compensationStep.get()).isEqualTo("ReverseTransaction");
  }

  @Test
  @DisplayName("defineProcess - CreatePayment should NOT have compensation (terminal)")
  void testDefineProcess_CreatePaymentNoCompensation() {
    // When
    boolean requiresCompensation = processGraph.requiresCompensation("CreatePayment");

    // Then
    assertThat(requiresCompensation).isFalse();
  }

  @Test
  @DisplayName("defineProcess - CreatePayment should be terminal (no next step)")
  void testDefineProcess_CreatePaymentIsTerminal() {
    // Given
    Map<String, Object> state = new HashMap<>();

    // When
    Optional<String> nextStep = processGraph.getNextStep("CreatePayment", state);

    // Then
    assertThat(nextStep).isEmpty();
  }

  @Test
  @DisplayName("isRetryable - should return true for timeout errors")
  void testIsRetryable_Timeout() {
    assertThat(processDefinition.isRetryable("BookLimitsCommand", "Connection timeout occurred"))
        .isTrue();
  }

  @Test
  @DisplayName("isRetryable - should return true for connection errors")
  void testIsRetryable_Connection() {
    assertThat(processDefinition.isRetryable("BookFxCommand", "Database connection failed"))
        .isTrue();
  }

  @Test
  @DisplayName("isRetryable - should return true for temporary errors")
  void testIsRetryable_Temporary() {
    assertThat(processDefinition.isRetryable("CreateTransactionCommand", "Temporary failure"))
        .isTrue();
  }

  @Test
  @DisplayName("isRetryable - should return false for business logic errors")
  void testIsRetryable_BusinessError() {
    assertThat(processDefinition.isRetryable("BookLimitsCommand", "Insufficient funds")).isFalse();
  }

  @Test
  @DisplayName("isRetryable - should return false for null error")
  void testIsRetryable_NullError() {
    assertThat(processDefinition.isRetryable("BookLimitsCommand", null)).isFalse();
  }

  @Test
  @DisplayName("getMaxRetries - should return 3 for all steps")
  void testGetMaxRetries() {
    assertThat(processDefinition.getMaxRetries("BookLimitsCommand")).isEqualTo(3);
    assertThat(processDefinition.getMaxRetries("BookFxCommand")).isEqualTo(3);
    assertThat(processDefinition.getMaxRetries("CreateTransactionCommand")).isEqualTo(3);
    assertThat(processDefinition.getMaxRetries("CreatePaymentCommand")).isEqualTo(3);
  }
}
