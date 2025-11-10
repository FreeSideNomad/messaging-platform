package com.acme.reliable.processor.process;

import com.acme.reliable.command.CommandBus;
import com.acme.reliable.command.DomainCommand;
import com.acme.reliable.process.*;
import com.acme.reliable.repository.ProcessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProcessManager using mocks (no DI container needed)
 */
class ProcessManagerTest {

    private ProcessManager processManager;
    private ProcessRepository mockRepo;
    private CommandBus mockCommandBus;
    private TestProcessConfiguration testDefinition;

    @BeforeEach
    void setup() {
        mockRepo = mock(ProcessRepository.class);
        mockCommandBus = mock(CommandBus.class);
        var mockBeanContext = mock(io.micronaut.context.BeanContext.class);
        processManager = new ProcessManager(mockRepo, mockCommandBus, mockBeanContext);
        testDefinition = new TestProcessConfiguration();
        processManager.register(testDefinition);
    }

    @Test
    void testStartProcess_CreatesInstanceAndExecutesFirstStep() {
        // Given
        when(mockCommandBus.accept(any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID());

        // When
        UUID processId =
                processManager.startProcess("TestProcess", "test-123", Map.of("initialData", "value"));

        // Then
        assertNotNull(processId);

        // Verify process instance was inserted
        ArgumentCaptor<ProcessInstance> instanceCaptor = ArgumentCaptor.forClass(ProcessInstance.class);
        ArgumentCaptor<ProcessEvent> eventCaptor = ArgumentCaptor.forClass(ProcessEvent.class);
        verify(mockRepo).insert(instanceCaptor.capture(), eventCaptor.capture());

        ProcessInstance instance = instanceCaptor.getValue();
        assertEquals("TestProcess", instance.processType());
        assertEquals("test-123", instance.businessKey());
        assertEquals("Step1", instance.currentStep());
        assertEquals(ProcessStatus.NEW, instance.status());

        ProcessEvent event = eventCaptor.getValue();
        assertTrue(event instanceof ProcessEvent.ProcessStarted);

        // Verify first step was executed
        verify(mockCommandBus)
                .accept(eq("Step1"), eq(processId + ":Step1"), eq("test-123"), any(), any());
    }

    @Test
    void testHandleReply_StepCompleted_MovesToNextStep() {
        // Given
        UUID processId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();

        ProcessInstance runningInstance =
                new ProcessInstance(
                        processId,
                        "TestProcess",
                        "test-123",
                        ProcessStatus.RUNNING,
                        "Step1",
                        Map.of("data1", "value1"),
                        0,
                        java.time.Instant.now(),
                        java.time.Instant.now());

        when(mockRepo.findById(processId)).thenReturn(Optional.of(runningInstance));
        when(mockCommandBus.accept(any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID());

        CommandReply reply = CommandReply.completed(commandId, processId, Map.of("result", "success"));

        // When
        processManager.handleReply(processId, commandId, reply);

        // Then
        // Verify instance was updated with completed event (twice: once for completion, once for
        // executing next step)
        ArgumentCaptor<ProcessInstance> instanceCaptor = ArgumentCaptor.forClass(ProcessInstance.class);
        ArgumentCaptor<ProcessEvent> eventCaptor = ArgumentCaptor.forClass(ProcessEvent.class);
        verify(mockRepo, times(2)).update(instanceCaptor.capture(), eventCaptor.capture());

        // Get the first update (step completed)
        ProcessInstance updated = instanceCaptor.getAllValues().get(0);
        assertEquals("Step2", updated.currentStep());
        assertTrue(updated.data().containsKey("result"));

        ProcessEvent event = eventCaptor.getAllValues().get(0);
        assertTrue(event instanceof ProcessEvent.StepCompleted);

        // Verify next step was executed
        verify(mockCommandBus).accept(eq("Step2"), eq(processId + ":Step2"), any(), any(), any());
    }

    @Test
    void testHandleReply_LastStepCompleted_MarksProcessSucceeded() {
        // Given
        UUID processId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();

        ProcessInstance runningInstance =
                new ProcessInstance(
                        processId,
                        "TestProcess",
                        "test-123",
                        ProcessStatus.RUNNING,
                        "Step2", // Last step
                        Map.of("data1", "value1"),
                        0,
                        java.time.Instant.now(),
                        java.time.Instant.now());

        when(mockRepo.findById(processId)).thenReturn(Optional.of(runningInstance));

        CommandReply reply = CommandReply.completed(commandId, processId, Map.of("final", "result"));

        // When
        processManager.handleReply(processId, commandId, reply);

        // Then
        ArgumentCaptor<ProcessInstance> instanceCaptor = ArgumentCaptor.forClass(ProcessInstance.class);
        ArgumentCaptor<ProcessEvent> eventCaptor = ArgumentCaptor.forClass(ProcessEvent.class);
        verify(mockRepo).update(instanceCaptor.capture(), eventCaptor.capture());

        ProcessInstance completed = instanceCaptor.getValue();
        assertEquals(ProcessStatus.SUCCEEDED, completed.status());

        ProcessEvent event = eventCaptor.getValue();
        assertTrue(event instanceof ProcessEvent.ProcessCompleted);

        // Verify no more steps executed
        verify(mockCommandBus, never()).accept(eq("Step2"), any(), any(), any(), any());
    }

    @Test
    void testHandleReply_StepFailed_RetriesWithBackoff() throws InterruptedException {
        // Given
        UUID processId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();

        ProcessInstance runningInstance =
                new ProcessInstance(
                        processId,
                        "TestProcess",
                        "test-123",
                        ProcessStatus.RUNNING,
                        "Step1",
                        Map.of("data1", "value1"),
                        0, // No retries yet
                        java.time.Instant.now(),
                        java.time.Instant.now());

        when(mockRepo.findById(processId)).thenReturn(Optional.of(runningInstance));
        when(mockCommandBus.accept(any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID());

        CommandReply reply = CommandReply.failed(commandId, processId, "Temporary connection timeout");

        // When
        long start = System.currentTimeMillis();
        processManager.handleReply(processId, commandId, reply);
        long duration = System.currentTimeMillis() - start;

        // Then
        // Verify retry was attempted
        ArgumentCaptor<ProcessInstance> instanceCaptor = ArgumentCaptor.forClass(ProcessInstance.class);
        verify(mockRepo, atLeastOnce()).update(instanceCaptor.capture(), any());

        ProcessInstance retried = instanceCaptor.getValue();
        assertEquals(1, retried.retries());

        // Verify backoff delay occurred (at least 1 second for first retry)
        assertTrue(
                duration >= 1000,
                "Expected backoff delay of at least 1 second, but was " + duration + "ms");

        // Verify step was retried
        verify(mockCommandBus, times(1))
                .accept(eq("Step1"), eq(processId + ":Step1"), any(), any(), any());
    }

    @Test
    void testHandleReply_DataMergingAcrossSteps() {
        // Given
        UUID processId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();

        // Initial process with some data
        ProcessInstance runningInstance =
                new ProcessInstance(
                        processId,
                        "TestProcess",
                        "test-123",
                        ProcessStatus.RUNNING,
                        "Step1",
                        Map.of("initialKey", "initialValue", "sharedKey", "originalValue"),
                        0,
                        java.time.Instant.now(),
                        java.time.Instant.now());

        when(mockRepo.findById(processId)).thenReturn(Optional.of(runningInstance));
        when(mockCommandBus.accept(any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID());

        // Reply with new data (including overriding sharedKey)
        CommandReply reply =
                CommandReply.completed(
                        commandId,
                        processId,
                        Map.of("step1Result", "success", "sharedKey", "updatedValue", "newKey", "newValue"));

        // When
        processManager.handleReply(processId, commandId, reply);

        // Then
        ArgumentCaptor<ProcessInstance> instanceCaptor = ArgumentCaptor.forClass(ProcessInstance.class);
        verify(mockRepo, times(2)).update(instanceCaptor.capture(), any());

        // Get the first update (step completed with merged data)
        ProcessInstance updated = instanceCaptor.getAllValues().get(0);

        // Verify data merging
        assertEquals("initialValue", updated.data().get("initialKey")); // Original preserved
        assertEquals("updatedValue", updated.data().get("sharedKey")); // Overridden by reply
        assertEquals("success", updated.data().get("step1Result")); // New from reply
        assertEquals("newValue", updated.data().get("newKey")); // New from reply
        assertEquals(4, updated.data().size()); // Total keys
    }

    @Test
    void testHandleReply_MaxRetriesExceeded_FailsPermanently() {
        // Given
        UUID processId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();

        // Process already at max retries
        ProcessInstance runningInstance =
                new ProcessInstance(
                        processId,
                        "TestProcess",
                        "test-123",
                        ProcessStatus.RUNNING,
                        "Step1",
                        Map.of("data1", "value1"),
                        3, // At max retries
                        java.time.Instant.now(),
                        java.time.Instant.now());

        when(mockRepo.findById(processId)).thenReturn(Optional.of(runningInstance));

        CommandReply reply = CommandReply.failed(commandId, processId, "Still failing after timeout");

        // When
        processManager.handleReply(processId, commandId, reply);

        // Then
        ArgumentCaptor<ProcessInstance> instanceCaptor = ArgumentCaptor.forClass(ProcessInstance.class);
        ArgumentCaptor<ProcessEvent> eventCaptor = ArgumentCaptor.forClass(ProcessEvent.class);
        verify(mockRepo, atLeastOnce()).update(instanceCaptor.capture(), eventCaptor.capture());

        ProcessInstance failed = instanceCaptor.getValue();
        assertEquals(ProcessStatus.FAILED, failed.status());

        // Verify no more commands sent
        verify(mockCommandBus, never()).accept(any(), any(), any(), any(), any());
    }

    @Test
    void testHandleReply_NonRetryableError_FailsImmediately() {
        // Given
        UUID processId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();

        ProcessInstance runningInstance =
                new ProcessInstance(
                        processId,
                        "TestProcess",
                        "test-123",
                        ProcessStatus.RUNNING,
                        "Step1",
                        Map.of("data1", "value1"),
                        0,
                        java.time.Instant.now(),
                        java.time.Instant.now());

        when(mockRepo.findById(processId)).thenReturn(Optional.of(runningInstance));

        // Non-retryable error (doesn't contain "timeout")
        CommandReply reply = CommandReply.failed(commandId, processId, "Invalid input data");

        // When
        processManager.handleReply(processId, commandId, reply);

        // Then
        ArgumentCaptor<ProcessInstance> instanceCaptor = ArgumentCaptor.forClass(ProcessInstance.class);
        verify(mockRepo, atLeastOnce()).update(instanceCaptor.capture(), any());

        ProcessInstance failed = instanceCaptor.getValue();
        assertEquals(ProcessStatus.FAILED, failed.status());

        // Verify no retry attempted
        verify(mockCommandBus, never()).accept(any(), any(), any(), any(), any());
    }

    @Test
    void testHandleReply_TimedOut_FailsPermanently() {
        // Given
        UUID processId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();

        ProcessInstance runningInstance =
                new ProcessInstance(
                        processId,
                        "TestProcess",
                        "test-123",
                        ProcessStatus.RUNNING,
                        "Step1",
                        Map.of("data1", "value1"),
                        0,
                        java.time.Instant.now(),
                        java.time.Instant.now());

        when(mockRepo.findById(processId)).thenReturn(Optional.of(runningInstance));

        CommandReply reply =
                CommandReply.timedOut(commandId, processId, "Command exceeded maximum wait time");

        // When
        processManager.handleReply(processId, commandId, reply);

        // Then
        ArgumentCaptor<ProcessInstance> instanceCaptor = ArgumentCaptor.forClass(ProcessInstance.class);
        ArgumentCaptor<ProcessEvent> eventCaptor = ArgumentCaptor.forClass(ProcessEvent.class);
        verify(mockRepo).update(instanceCaptor.capture(), eventCaptor.capture());

        // Final status should be FAILED
        ProcessInstance failed = instanceCaptor.getValue();
        assertEquals(ProcessStatus.FAILED, failed.status());

        // The event passed to update() should be ProcessFailed (created in handlePermanentFailure)
        ProcessEvent event = eventCaptor.getValue();
        assertTrue(event instanceof ProcessEvent.ProcessFailed);
    }

    @Test
    void testStartProcess_InitialDataPassedToFirstStep() {
        // Given
        Map<String, Object> initialData =
                Map.of(
                        "customerId", "cust-123",
                        "amount", 100.00,
                        "currency", "USD");

        when(mockCommandBus.accept(any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID());

        // When
        UUID processId = processManager.startProcess("TestProcess", "test-business-key", initialData);

        // Then
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockCommandBus)
                .accept(
                        eq("Step1"),
                        eq(processId + ":Step1"),
                        eq("test-business-key"),
                        payloadCaptor.capture(),
                        any());

        // Verify initial data is in the command payload
        String payload = payloadCaptor.getValue();
        assertTrue(payload.contains("customerId"));
        assertTrue(payload.contains("cust-123"));
        assertTrue(payload.contains("amount"));
        assertTrue(payload.contains("currency"));
    }

    @Test
    void testStartProcess_UnknownProcessType_ThrowsException() {
        // Given
        Map<String, Object> initialData = Map.of("key", "value");

        // When/Then
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    processManager.startProcess("UnknownProcess", "test-key", initialData);
                });
    }

    @Test
    void testHandleReply_UnknownProcess_LogsWarningAndReturns() {
        // Given
        UUID unknownProcessId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();

        when(mockRepo.findById(unknownProcessId)).thenReturn(Optional.empty());

        CommandReply reply =
                CommandReply.completed(commandId, unknownProcessId, Map.of("result", "success"));

        // When
        processManager.handleReply(unknownProcessId, commandId, reply);

        // Then
        // Should not throw, just log warning
        verify(mockRepo).findById(unknownProcessId);
        verify(mockRepo, never()).update(any(), any());
        verify(mockCommandBus, never()).accept(any(), any(), any(), any(), any());
    }

    @Test
    void testStartProcess_DuplicateProcessType_ThrowsException() {
        // Given
        TestProcessConfiguration duplicateConfig = new TestProcessConfiguration();

        // When/Then
        assertThrows(
                IllegalStateException.class,
                () -> {
                    processManager.register(duplicateConfig);
                },
                "Should not allow duplicate process type registration");
    }

    @Test
    void testHandleReply_EmptyPayload_ProcessesSuccessfully() {
        // Given
        UUID processId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();

        ProcessInstance runningInstance =
                new ProcessInstance(
                        processId,
                        "TestProcess",
                        "test-123",
                        ProcessStatus.RUNNING,
                        "Step2",
                        Map.of("existingData", "value"),
                        0,
                        java.time.Instant.now(),
                        java.time.Instant.now());

        when(mockRepo.findById(processId)).thenReturn(Optional.of(runningInstance));

        CommandReply reply = CommandReply.completed(commandId, processId, Map.of());

        // When
        processManager.handleReply(processId, commandId, reply);

        // Then
        ArgumentCaptor<ProcessInstance> instanceCaptor = ArgumentCaptor.forClass(ProcessInstance.class);
        verify(mockRepo).update(instanceCaptor.capture(), any());

        ProcessInstance completed = instanceCaptor.getValue();
        assertEquals(ProcessStatus.SUCCEEDED, completed.status());
        assertTrue(completed.data().containsKey("existingData"));
    }

    @Test
    void testHandleReply_ProcessInNewStatus_CompletesNormally() {
        // Given
        UUID processId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();

        ProcessInstance newInstance =
                new ProcessInstance(
                        processId,
                        "TestProcess",
                        "test-123",
                        ProcessStatus.NEW,
                        "Step1",
                        Map.of("data", "value"),
                        0,
                        java.time.Instant.now(),
                        java.time.Instant.now());

        when(mockRepo.findById(processId)).thenReturn(Optional.of(newInstance));
        when(mockCommandBus.accept(any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID());

        CommandReply reply = CommandReply.completed(commandId, processId, Map.of("result", "ok"));

        // When
        processManager.handleReply(processId, commandId, reply);

        // Then
        verify(mockRepo, times(2)).update(any(), any());
    }

    @Test
    void testHandleReply_RetryWithMultipleBackoffs() throws InterruptedException {
        // Given
        UUID processId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();

        for (int retry = 0; retry < 3; retry++) {
            ProcessInstance runningInstance =
                    new ProcessInstance(
                            processId,
                            "TestProcess",
                            "test-123",
                            ProcessStatus.RUNNING,
                            "Step1",
                            Map.of("data", "value"),
                            retry,
                            java.time.Instant.now(),
                            java.time.Instant.now());

            when(mockRepo.findById(processId)).thenReturn(Optional.of(runningInstance));
            when(mockCommandBus.accept(any(), any(), any(), any(), any()))
                    .thenReturn(UUID.randomUUID());

            CommandReply reply = CommandReply.failed(commandId, processId, "Connection timeout error");

            long start = System.currentTimeMillis();
            processManager.handleReply(processId, commandId, reply);
            long duration = System.currentTimeMillis() - start;

            // Verify backoff delay increases with each retry (exponential backoff)
            long expectedMinDelay = (long) Math.pow(2, retry) * 1000;
            assertTrue(
                    duration >= expectedMinDelay,
                    "Expected backoff delay of at least "
                            + expectedMinDelay
                            + "ms for retry "
                            + retry
                            + ", but was "
                            + duration
                            + "ms");
        }

        // After max retries, should fail permanently
        ProcessInstance maxRetriesInstance =
                new ProcessInstance(
                        processId,
                        "TestProcess",
                        "test-123",
                        ProcessStatus.RUNNING,
                        "Step1",
                        Map.of("data", "value"),
                        3,
                        java.time.Instant.now(),
                        java.time.Instant.now());

        when(mockRepo.findById(processId)).thenReturn(Optional.of(maxRetriesInstance));

        CommandReply finalReply = CommandReply.failed(commandId, processId, "Still failing timeout");
        processManager.handleReply(processId, commandId, finalReply);

        ArgumentCaptor<ProcessInstance> instanceCaptor = ArgumentCaptor.forClass(ProcessInstance.class);
        verify(mockRepo, atLeastOnce()).update(instanceCaptor.capture(), any());

        ProcessInstance failed = instanceCaptor.getValue();
        assertEquals(ProcessStatus.FAILED, failed.status());
    }

    @Test
    void testStartProcess_ProcessExecutionFailure_MarksAsFailed() {
        // Given
        when(mockCommandBus.accept(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Command bus unavailable"));

        // When/Then
        assertThrows(
                RuntimeException.class,
                () -> {
                    processManager.startProcess("TestProcess", "test-key", Map.of("data", "value"));
                });

        // Verify process was marked as failed
        ArgumentCaptor<ProcessInstance> instanceCaptor = ArgumentCaptor.forClass(ProcessInstance.class);
        verify(mockRepo).update(instanceCaptor.capture(), any());

        ProcessInstance failed = instanceCaptor.getValue();
        assertEquals(ProcessStatus.FAILED, failed.status());
    }

    @Test
    void testHandleReply_SuccessiveStepsWithAccumulatingData() {
        // Given
        UUID processId = UUID.randomUUID();

        // Create a 3-step process configuration
        ThreeStepProcessConfiguration threeStepConfig = new ThreeStepProcessConfiguration();
        processManager.register(threeStepConfig);

        // Start with step1 data
        ProcessInstance step1Instance =
                new ProcessInstance(
                        processId,
                        "ThreeStepProcess",
                        "test-key",
                        ProcessStatus.RUNNING,
                        "StepA",
                        Map.of("step1Data", "valueA"),
                        0,
                        java.time.Instant.now(),
                        java.time.Instant.now());

        when(mockRepo.findById(processId)).thenReturn(Optional.of(step1Instance));
        when(mockCommandBus.accept(any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID());

        // Step 1 completion
        CommandReply reply1 =
                CommandReply.completed(
                        UUID.randomUUID(), processId, Map.of("step2Data", "valueB", "stepCount", 1));
        processManager.handleReply(processId, UUID.randomUUID(), reply1);

        // Update to step2
        ProcessInstance step2Instance =
                new ProcessInstance(
                        processId,
                        "ThreeStepProcess",
                        "test-key",
                        ProcessStatus.RUNNING,
                        "StepB",
                        Map.of("step1Data", "valueA", "step2Data", "valueB", "stepCount", 1),
                        0,
                        java.time.Instant.now(),
                        java.time.Instant.now());

        when(mockRepo.findById(processId)).thenReturn(Optional.of(step2Instance));

        // Step 2 completion
        CommandReply reply2 =
                CommandReply.completed(
                        UUID.randomUUID(), processId, Map.of("step3Data", "valueC", "stepCount", 2));
        processManager.handleReply(processId, UUID.randomUUID(), reply2);

        // Update to step3
        ProcessInstance step3Instance =
                new ProcessInstance(
                        processId,
                        "ThreeStepProcess",
                        "test-key",
                        ProcessStatus.RUNNING,
                        "StepC",
                        Map.of(
                                "step1Data", "valueA",
                                "step2Data", "valueB",
                                "step3Data", "valueC",
                                "stepCount", 2),
                        0,
                        java.time.Instant.now(),
                        java.time.Instant.now());

        when(mockRepo.findById(processId)).thenReturn(Optional.of(step3Instance));

        // Step 3 completion (final)
        CommandReply reply3 =
                CommandReply.completed(UUID.randomUUID(), processId, Map.of("finalData", "done"));
        processManager.handleReply(processId, UUID.randomUUID(), reply3);

        // Then - verify final state has all accumulated data
        ArgumentCaptor<ProcessInstance> instanceCaptor = ArgumentCaptor.forClass(ProcessInstance.class);
        verify(mockRepo, atLeastOnce()).update(instanceCaptor.capture(), any());

        // Get the last update (completed state)
        ProcessInstance completed = instanceCaptor.getValue();
        assertEquals(ProcessStatus.SUCCEEDED, completed.status());
        assertTrue(completed.data().containsKey("step1Data"));
        assertTrue(completed.data().containsKey("step2Data"));
        assertTrue(completed.data().containsKey("step3Data"));
        assertTrue(completed.data().containsKey("finalData"));
    }

    @Test
    void testHandleReply_MixedSuccessAndRetryScenario() {
        // Given
        UUID processId = UUID.randomUUID();

        // Step 1 completes successfully
        ProcessInstance step1Instance =
                new ProcessInstance(
                        processId,
                        "TestProcess",
                        "test-key",
                        ProcessStatus.RUNNING,
                        "Step1",
                        Map.of("initial", "data"),
                        0,
                        java.time.Instant.now(),
                        java.time.Instant.now());

        when(mockRepo.findById(processId)).thenReturn(Optional.of(step1Instance));
        when(mockCommandBus.accept(any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID());

        CommandReply successReply =
                CommandReply.completed(UUID.randomUUID(), processId, Map.of("step1Result", "success"));
        processManager.handleReply(processId, UUID.randomUUID(), successReply);

        // Step 2 fails with retryable error
        ProcessInstance step2Instance =
                new ProcessInstance(
                        processId,
                        "TestProcess",
                        "test-key",
                        ProcessStatus.RUNNING,
                        "Step2",
                        Map.of("initial", "data", "step1Result", "success"),
                        0,
                        java.time.Instant.now(),
                        java.time.Instant.now());

        when(mockRepo.findById(processId)).thenReturn(Optional.of(step2Instance));

        CommandReply retryableFailure =
                CommandReply.failed(UUID.randomUUID(), processId, "Temporary timeout");
        processManager.handleReply(processId, UUID.randomUUID(), retryableFailure);

        // Then verify retry was attempted
        verify(mockCommandBus, atLeast(2)).accept(eq("Step2"), any(), any(), any(), any());
    }

    @Test
    void testHandleReply_ProcessAlreadyInSucceededStatus_NoOp() {
        // Given
        UUID processId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();

        ProcessInstance succeededInstance =
                new ProcessInstance(
                        processId,
                        "TestProcess",
                        "test-key",
                        ProcessStatus.SUCCEEDED,
                        "Step2",
                        Map.of("data", "value"),
                        0,
                        java.time.Instant.now(),
                        java.time.Instant.now());

        when(mockRepo.findById(processId)).thenReturn(Optional.of(succeededInstance));

        CommandReply lateReply = CommandReply.completed(commandId, processId, Map.of());

        // When
        processManager.handleReply(processId, commandId, lateReply);

        // Then - should still process the reply even if already succeeded
        verify(mockRepo).findById(processId);
    }

    @Test
    void testStartProcess_WithEmptyInitialData() {
        // Given
        when(mockCommandBus.accept(any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID());

        // When
        UUID processId = processManager.startProcess("TestProcess", "test-key", Map.of());

        // Then
        assertNotNull(processId);

        ArgumentCaptor<ProcessInstance> instanceCaptor = ArgumentCaptor.forClass(ProcessInstance.class);
        verify(mockRepo).insert(instanceCaptor.capture(), any());

        ProcessInstance instance = instanceCaptor.getValue();
        assertNotNull(instance.data());
        assertTrue(instance.data().isEmpty());
    }

    @Test
    void testHandleReply_FailedReply_WithNullError() {
        // Given
        UUID processId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();

        ProcessInstance runningInstance =
                new ProcessInstance(
                        processId,
                        "TestProcess",
                        "test-123",
                        ProcessStatus.RUNNING,
                        "Step1",
                        Map.of("data", "value"),
                        0,
                        java.time.Instant.now(),
                        java.time.Instant.now());

        when(mockRepo.findById(processId)).thenReturn(Optional.of(runningInstance));

        CommandReply reply = CommandReply.failed(commandId, processId, null);

        // When
        processManager.handleReply(processId, commandId, reply);

        // Then - null error should not cause crash, treated as non-retryable
        verify(mockRepo, atLeastOnce()).update(any(), any());
    }

    @Test
    void testHandleReply_LargePayloadData() {
        // Given
        UUID processId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();

        ProcessInstance runningInstance =
                new ProcessInstance(
                        processId,
                        "TestProcess",
                        "test-123",
                        ProcessStatus.RUNNING,
                        "Step1",
                        Map.of("small", "data"),
                        0,
                        java.time.Instant.now(),
                        java.time.Instant.now());

        when(mockRepo.findById(processId)).thenReturn(Optional.of(runningInstance));
        when(mockCommandBus.accept(any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID());

        // Create large payload with many keys
        Map<String, Object> largePayload = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            largePayload.put("key" + i, "value" + i);
            largePayload.put("nested" + i, Map.of("subKey", "subValue" + i));
        }

        CommandReply reply = CommandReply.completed(commandId, processId, largePayload);

        // When
        processManager.handleReply(processId, commandId, reply);

        // Then - should handle large payload without issues
        ArgumentCaptor<ProcessInstance> instanceCaptor = ArgumentCaptor.forClass(ProcessInstance.class);
        verify(mockRepo, times(2)).update(instanceCaptor.capture(), any());

        ProcessInstance updated = instanceCaptor.getAllValues().get(0);
        assertTrue(updated.data().size() > 100);
    }

    @Test
    void testOnApplicationEvent_AutoDiscoveryRegistersProcesses() {
        // Given
        var mockBeanContext = mock(io.micronaut.context.BeanContext.class);
        var mockServerEvent = mock(io.micronaut.runtime.server.event.ServerStartupEvent.class);

        ProcessManager manager = new ProcessManager(mockRepo, mockCommandBus, mockBeanContext);

        TestProcessConfiguration config1 = new TestProcessConfiguration();
        AnotherTestProcessConfiguration config2 = new AnotherTestProcessConfiguration();

        when(mockBeanContext.getBeansOfType(ProcessConfiguration.class))
                .thenReturn(java.util.List.of(config1, config2));

        // When
        manager.onApplicationEvent(mockServerEvent);

        // Then - both configurations should be registered
        // Verify by attempting to start each process type
        when(mockCommandBus.accept(any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID());

        assertDoesNotThrow(
                () -> manager.startProcess("TestProcess", "key1", Map.of()),
                "TestProcess should be registered");

        assertDoesNotThrow(
                () -> manager.startProcess("AnotherTestProcess", "key2", Map.of()),
                "AnotherTestProcess should be registered");
    }

    @Test
    void testOnApplicationEvent_DuplicateConfiguration_ThrowsException() {
        // Given
        var mockBeanContext = mock(io.micronaut.context.BeanContext.class);
        var mockServerEvent = mock(io.micronaut.runtime.server.event.ServerStartupEvent.class);

        ProcessManager manager = new ProcessManager(mockRepo, mockCommandBus, mockBeanContext);

        TestProcessConfiguration config1 = new TestProcessConfiguration();
        TestProcessConfiguration config2 = new TestProcessConfiguration(); // Same type

        when(mockBeanContext.getBeansOfType(ProcessConfiguration.class))
                .thenReturn(java.util.List.of(config1, config2));

        // When/Then
        assertThrows(
                IllegalStateException.class,
                () -> manager.onApplicationEvent(mockServerEvent),
                "Should throw when duplicate process types are discovered");
    }

    /**
     * Test ProcessConfiguration for unit tests
     */
    static class TestProcessConfiguration implements ProcessConfiguration {
        @Override
        public String getProcessType() {
            return "TestProcess";
        }

        @Override
        public ProcessGraph defineProcess() {
            return ProcessGraphBuilder.process()
                    .startWith(Step1Command.class)
                    .then(Step2Command.class)
                    .end();
        }

        @Override
        public boolean isRetryable(String step, String error) {
            return error != null && error.contains("timeout");
        }

        @Override
        public int getMaxRetries(String step) {
            return 3;
        }
    }

    // Dummy command classes for testing
    static class Step1Command implements DomainCommand {
    }

    static class Step2Command implements DomainCommand {
    }

    /**
     * Three-step process configuration for testing data accumulation
     */
    static class ThreeStepProcessConfiguration implements ProcessConfiguration {
        @Override
        public String getProcessType() {
            return "ThreeStepProcess";
        }

        @Override
        public ProcessGraph defineProcess() {
            return ProcessGraphBuilder.process()
                    .startWith(StepACommand.class)
                    .then(StepBCommand.class)
                    .then(StepCCommand.class)
                    .end();
        }

        @Override
        public boolean isRetryable(String step, String error) {
            return error != null && error.contains("timeout");
        }

        @Override
        public int getMaxRetries(String step) {
            return 3;
        }
    }

    /**
     * Another test process configuration for auto-discovery tests
     */
    static class AnotherTestProcessConfiguration implements ProcessConfiguration {
        @Override
        public String getProcessType() {
            return "AnotherTestProcess";
        }

        @Override
        public ProcessGraph defineProcess() {
            return ProcessGraphBuilder.process().startWith(Step1Command.class).end();
        }

        @Override
        public boolean isRetryable(String step, String error) {
            return false;
        }

        @Override
        public int getMaxRetries(String step) {
            return 0;
        }
    }

    // Additional dummy command classes
    static class StepACommand implements DomainCommand {
    }

    static class StepBCommand implements DomainCommand {
    }

    static class StepCCommand implements DomainCommand {
    }
}
