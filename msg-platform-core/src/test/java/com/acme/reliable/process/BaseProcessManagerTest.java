package com.acme.reliable.process;

import com.acme.reliable.command.CommandBus;
import com.acme.reliable.command.DomainCommand;
import com.acme.reliable.repository.ProcessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.acme.reliable.process.ProcessGraphBuilder.process;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BaseProcessManager
 */
class BaseProcessManagerTest {

    private TestProcessManager manager;
    private ProcessRepository mockRepository;
    private CommandBus mockCommandBus;
    private List<Runnable> transactionCallbacks;

    // Test command classes
    public record Step1Command() implements DomainCommand {}
    public record Step2Command() implements DomainCommand {}
    public record Step3Command() implements DomainCommand {}
    public record CompensateStep1Command() implements DomainCommand {}

    @BeforeEach
    void setUp() {
        mockRepository = mock(ProcessRepository.class);
        mockCommandBus = mock(CommandBus.class);
        transactionCallbacks = new ArrayList<>();

        manager = new TestProcessManager(mockRepository, mockCommandBus, transactionCallbacks);
    }

    @Nested
    @DisplayName("Process Registration Tests")
    class RegistrationTests {

        @Test
        @DisplayName("register - should register process configuration")
        void testRegister() {
            ProcessConfiguration config = new SimpleTestProcessConfiguration();

            manager.register(config);

            ProcessConfiguration retrieved = manager.getConfiguration("TestProcess");
            assertThat(retrieved).isNotNull();
            assertThat(retrieved.getProcessType()).isEqualTo("TestProcess");
        }

        @Test
        @DisplayName("register - should build process graph")
        void testRegisterBuildsGraph() {
            ProcessConfiguration config = new SimpleTestProcessConfiguration();

            manager.register(config);

            ProcessGraph graph = manager.getGraph("TestProcess");
            assertThat(graph).isNotNull();
            assertThat(graph.getInitialStep()).isEqualTo("Step1");
        }

        @Test
        @DisplayName("getConfiguration - should throw exception for unknown process type")
        void testGetConfigurationUnknownType() {
            assertThatThrownBy(() -> manager.getConfiguration("UnknownProcess"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown process type");
        }

        @Test
        @DisplayName("getGraph - should throw exception for unknown process type")
        void testGetGraphUnknownType() {
            assertThatThrownBy(() -> manager.getGraph("UnknownProcess"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown process type");
        }
    }

    @Nested
    @DisplayName("Process Start Tests")
    class StartProcessTests {

        @Test
        @DisplayName("startProcess - should create and start new process")
        void testStartProcess() {
            // Setup
            UUID commandId = UUID.randomUUID();
            when(mockCommandBus.accept(anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(commandId);

            ProcessConfiguration config = new SimpleTestProcessConfiguration();
            manager.register(config);

            Map<String, Object> initialData = Map.of("key", "value");

            // Execute
            UUID processId = manager.startProcess("TestProcess", "biz-123", initialData);

            // Verify
            assertThat(processId).isNotNull();
            assertThat(transactionCallbacks).hasSize(1);

            // Execute transaction
            transactionCallbacks.get(0).run();

            // Verify process inserted
            verify(mockRepository).insert(argThat(instance ->
                instance.processId().equals(processId) &&
                instance.processType().equals("TestProcess") &&
                instance.businessKey().equals("biz-123") &&
                instance.status() == ProcessStatus.NEW &&
                instance.currentStep().equals("Step1") &&
                instance.data().containsKey("key")
            ), any(ProcessEvent.class));

            // Verify command sent
            verify(mockCommandBus).accept(
                eq("Step1"),
                contains(processId.toString()),
                eq("biz-123"),
                anyString(),
                anyMap()
            );

            // Verify process updated to RUNNING
            verify(mockRepository).update(argThat(instance ->
                instance.status() == ProcessStatus.RUNNING
            ), any(ProcessEvent.class));
        }

        @Test
        @DisplayName("startProcess - should throw exception for unknown process type")
        void testStartProcessUnknownType() {
            Map<String, Object> initialData = Map.of();

            assertThatThrownBy(() -> manager.startProcess("UnknownProcess", "key", initialData))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown process type");
        }

        @Test
        @DisplayName("startProcess - should handle empty initial data")
        void testStartProcessEmptyData() {
            UUID commandId = UUID.randomUUID();
            when(mockCommandBus.accept(anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(commandId);

            ProcessConfiguration config = new SimpleTestProcessConfiguration();
            manager.register(config);

            UUID processId = manager.startProcess("TestProcess", "key", Map.of());

            assertThat(processId).isNotNull();
        }
    }

    @Nested
    @DisplayName("Reply Handling Tests")
    class ReplyHandlingTests {

        @Test
        @DisplayName("handleReply - should handle completed reply and move to next step")
        void testHandleReplyCompleted() {
            UUID processId = UUID.randomUUID();
            UUID commandId = UUID.randomUUID();
            UUID nextCommandId = UUID.randomUUID();

            // Setup process
            ProcessInstance instance = ProcessInstance.create(
                processId,
                "TestProcess",
                "key",
                "Step1",
                Map.of("key", "value")
            ).withStatus(ProcessStatus.RUNNING);

            when(mockRepository.findById(processId)).thenReturn(Optional.of(instance));
            when(mockCommandBus.accept(anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(nextCommandId);

            ProcessConfiguration config = new SimpleTestProcessConfiguration();
            manager.register(config);

            CommandReply reply = CommandReply.completed(
                commandId,
                processId,
                Map.of("result", "success")
            );

            // Execute
            manager.handleReply(processId, commandId, reply);

            // Verify transaction executed
            assertThat(transactionCallbacks).hasSize(1);
            transactionCallbacks.get(0).run();

            // Verify process was updated
            verify(mockRepository, atLeastOnce()).update(
                any(ProcessInstance.class),
                any(ProcessEvent.class)
            );
        }

        @Test
        @DisplayName("handleReply - should complete process when no next step")
        void testHandleReplyCompletesProcess() {
            UUID processId = UUID.randomUUID();
            UUID commandId = UUID.randomUUID();

            ProcessInstance instance = ProcessInstance.create(
                processId,
                "TestProcess",
                "key",
                "Step3",
                Map.of()
            ).withStatus(ProcessStatus.RUNNING);

            when(mockRepository.findById(processId)).thenReturn(Optional.of(instance));

            ProcessConfiguration config = new SimpleTestProcessConfiguration();
            manager.register(config);

            CommandReply reply = CommandReply.completed(commandId, processId, Map.of());

            // Execute
            manager.handleReply(processId, commandId, reply);
            transactionCallbacks.get(0).run();

            // Verify process marked as SUCCEEDED
            verify(mockRepository).update(
                argThat(inst -> inst.status() == ProcessStatus.SUCCEEDED),
                argThat(event -> event instanceof ProcessEvent.ProcessCompleted)
            );
        }

        @Test
        @DisplayName("handleReply - should handle failed reply with retry")
        void testHandleReplyFailedWithRetry() {
            UUID processId = UUID.randomUUID();
            UUID commandId = UUID.randomUUID();
            UUID retryCommandId = UUID.randomUUID();

            ProcessInstance instance = ProcessInstance.create(
                processId,
                "TestProcess",
                "key",
                "Step1",
                Map.of()
            ).withStatus(ProcessStatus.RUNNING);

            when(mockRepository.findById(processId)).thenReturn(Optional.of(instance));
            when(mockCommandBus.accept(anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(retryCommandId);

            ProcessConfiguration config = new RetryableTestProcessConfiguration();
            manager.register(config);

            CommandReply reply = CommandReply.failed(commandId, processId, "timeout error");

            // Execute
            manager.handleReply(processId, commandId, reply);
            transactionCallbacks.get(0).run();

            // Verify process retries incremented
            verify(mockRepository).update(
                argThat(inst -> inst.retries() == 1),
                argThat(event -> event instanceof ProcessEvent.StepFailed)
            );

            // Verify retry command sent
            verify(mockCommandBus).accept(
                eq("Step1"),
                anyString(),
                anyString(),
                anyString(),
                anyMap()
            );
        }

        @Test
        @DisplayName("handleReply - should handle failed reply without retry")
        void testHandleReplyFailedPermanent() {
            UUID processId = UUID.randomUUID();
            UUID commandId = UUID.randomUUID();

            ProcessInstance instance = ProcessInstance.create(
                processId,
                "TestProcess",
                "key",
                "Step1",
                Map.of()
            ).withStatus(ProcessStatus.RUNNING);

            when(mockRepository.findById(processId)).thenReturn(Optional.of(instance));

            ProcessConfiguration config = new NoRetryTestProcessConfiguration();
            manager.register(config);

            CommandReply reply = CommandReply.failed(commandId, processId, "PermanentError");

            // Execute
            manager.handleReply(processId, commandId, reply);
            transactionCallbacks.get(0).run();

            // Verify process marked as FAILED
            verify(mockRepository).update(
                argThat(inst -> inst.status() == ProcessStatus.FAILED),
                argThat(event -> event instanceof ProcessEvent.ProcessFailed)
            );
        }

        @Test
        @DisplayName("handleReply - should handle timed out reply")
        void testHandleReplyTimedOut() {
            UUID processId = UUID.randomUUID();
            UUID commandId = UUID.randomUUID();

            ProcessInstance instance = ProcessInstance.create(
                processId,
                "TestProcess",
                "key",
                "Step1",
                Map.of()
            ).withStatus(ProcessStatus.RUNNING);

            when(mockRepository.findById(processId)).thenReturn(Optional.of(instance));

            ProcessConfiguration config = new SimpleTestProcessConfiguration();
            manager.register(config);

            CommandReply reply = CommandReply.timedOut(commandId, processId, "Timeout");

            // Execute
            manager.handleReply(processId, commandId, reply);
            transactionCallbacks.get(0).run();

            // Verify process marked as FAILED
            verify(mockRepository).update(
                argThat(inst -> inst.status() == ProcessStatus.FAILED),
                argThat(event -> event instanceof ProcessEvent.ProcessFailed)
            );
        }

        @Test
        @DisplayName("handleReply - should ignore reply for unknown process")
        void testHandleReplyUnknownProcess() {
            UUID processId = UUID.randomUUID();
            UUID commandId = UUID.randomUUID();

            when(mockRepository.findById(processId)).thenReturn(Optional.empty());

            CommandReply reply = CommandReply.completed(commandId, processId, Map.of());

            // Execute - should not throw
            manager.handleReply(processId, commandId, reply);
            transactionCallbacks.get(0).run();

            // Verify no updates
            verify(mockRepository, never()).update(any(), any());
        }
    }

    @Nested
    @DisplayName("Compensation Tests")
    class CompensationTests {

        @Test
        @DisplayName("should start compensation on failure when required")
        void testCompensationOnFailure() {
            UUID processId = UUID.randomUUID();
            UUID commandId = UUID.randomUUID();
            UUID compensationCommandId = UUID.randomUUID();

            ProcessInstance instance = ProcessInstance.create(
                processId,
                "TestProcess",
                "key",
                "Step1",
                Map.of()
            ).withStatus(ProcessStatus.RUNNING);

            when(mockRepository.findById(processId)).thenReturn(Optional.of(instance));
            when(mockCommandBus.accept(anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(compensationCommandId);

            ProcessConfiguration config = new CompensatingTestProcessConfiguration();
            manager.register(config);

            CommandReply reply = CommandReply.failed(commandId, processId, "Error");

            // Execute
            manager.handleReply(processId, commandId, reply);
            transactionCallbacks.get(0).run();

            // Verify compensation started - update called twice (compensating status + compensation event)
            verify(mockRepository, atLeast(1)).update(
                argThat(inst -> inst.status() == ProcessStatus.COMPENSATING),
                any(ProcessEvent.class)
            );

            verify(mockCommandBus).accept(
                eq("CompensateStep1"),
                contains("COMPENSATE"),
                anyString(),
                anyString(),
                anyMap()
            );
        }

        @Test
        @DisplayName("should mark as failed when no compensation required")
        void testNoCompensationRequired() {
            UUID processId = UUID.randomUUID();
            UUID commandId = UUID.randomUUID();

            ProcessInstance instance = ProcessInstance.create(
                processId,
                "TestProcess",
                "key",
                "Step1",
                Map.of()
            ).withStatus(ProcessStatus.RUNNING);

            when(mockRepository.findById(processId)).thenReturn(Optional.of(instance));

            ProcessConfiguration config = new NoRetryTestProcessConfiguration();
            manager.register(config);

            CommandReply reply = CommandReply.failed(commandId, processId, "Error");

            // Execute
            manager.handleReply(processId, commandId, reply);
            transactionCallbacks.get(0).run();

            // Verify marked as FAILED (no compensation)
            verify(mockRepository).update(
                argThat(inst -> inst.status() == ProcessStatus.FAILED),
                any(ProcessEvent.class)
            );
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("should handle max retries exceeded")
        void testMaxRetriesExceeded() {
            UUID processId = UUID.randomUUID();
            UUID commandId = UUID.randomUUID();

            ProcessInstance instance = ProcessInstance.create(
                processId,
                "TestProcess",
                "key",
                "Step1",
                Map.of()
            ).withStatus(ProcessStatus.RUNNING)
             .withRetries(3);

            when(mockRepository.findById(processId)).thenReturn(Optional.of(instance));

            ProcessConfiguration config = new RetryableTestProcessConfiguration();
            manager.register(config);

            CommandReply reply = CommandReply.failed(commandId, processId, "timeout error");

            // Execute
            manager.handleReply(processId, commandId, reply);
            transactionCallbacks.get(0).run();

            // Verify process failed (no more retries)
            verify(mockRepository).update(
                argThat(inst -> inst.status() == ProcessStatus.FAILED),
                argThat(event -> event instanceof ProcessEvent.ProcessFailed)
            );
        }

        @Test
        @DisplayName("should handle null data in process")
        void testNullDataHandling() {
            UUID commandId = UUID.randomUUID();
            when(mockCommandBus.accept(anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(commandId);

            ProcessConfiguration config = new SimpleTestProcessConfiguration();
            manager.register(config);

            // Should not throw with empty data
            UUID processId = manager.startProcess("TestProcess", "key", Map.of());
            assertThat(processId).isNotNull();
        }

        @Test
        @DisplayName("register - should throw exception for duplicate registration")
        void testDuplicateRegistration() {
            ProcessConfiguration config = new SimpleTestProcessConfiguration();

            manager.register(config);

            // Attempt to register again should throw
            assertThatThrownBy(() -> manager.register(config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Process already registered");
        }

        @Test
        @DisplayName("register - should handle null graph gracefully")
        void testRegisterNullGraph() {
            ProcessConfiguration config = new ProcessConfiguration() {
                @Override
                public String getProcessType() {
                    return "NullGraphProcess";
                }

                @Override
                public ProcessGraph defineProcess() {
                    return null;
                }
            };

            manager.register(config);

            // Should throw when trying to start process with null graph
            assertThatThrownBy(() -> manager.startProcess("NullGraphProcess", "key", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown process type");
        }

        @Test
        @DisplayName("startProcess - should handle exception during initial step execution")
        void testStartProcessExecutionFailure() {
            when(mockCommandBus.accept(anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenThrow(new RuntimeException("Command bus failure"));

            ProcessConfiguration config = new SimpleTestProcessConfiguration();
            manager.register(config);

            // Execute and verify exception is propagated
            assertThatThrownBy(() -> {
                UUID processId = manager.startProcess("TestProcess", "key", Map.of());
                transactionCallbacks.get(0).run();
            }).isInstanceOf(RuntimeException.class)
              .hasMessageContaining("Command bus failure");
        }

        @Test
        @DisplayName("should handle compensation with no compensation step")
        void testCompensationWithNoStep() {
            UUID processId = UUID.randomUUID();
            UUID commandId = UUID.randomUUID();

            ProcessInstance instance = ProcessInstance.create(
                processId,
                "TestProcess",
                "key",
                "Step1",
                Map.of()
            ).withStatus(ProcessStatus.RUNNING);

            when(mockRepository.findById(processId)).thenReturn(Optional.of(instance));

            // Configuration requires compensation but has no compensation step
            ProcessConfiguration config = new ProcessConfiguration() {
                @Override
                public String getProcessType() {
                    return "TestProcess";
                }

                @Override
                public ProcessGraph defineProcess() {
                    // Create a custom ProcessGraph with compensation but no compensation step
                    Map<String, ProcessStep> steps = new HashMap<>();
                    steps.put("Step1", new ProcessStep(
                        "Step1",
                        null, // Compensation is null but requiresCompensation will return false
                        new ProcessStep.Terminal()
                    ));
                    return new TestProcessGraph("Step1", steps, true, false);
                }

                @Override
                public int getMaxRetries(String step) {
                    return 0;
                }
            };

            manager.register(config);

            CommandReply reply = CommandReply.failed(commandId, processId, "Error");

            // Execute
            manager.handleReply(processId, commandId, reply);
            transactionCallbacks.get(0).run();

            // Verify marked as COMPENSATED (no action needed)
            verify(mockRepository, atLeastOnce()).update(
                argThat(inst -> inst.status() == ProcessStatus.COMPENSATED),
                any(ProcessEvent.class)
            );
        }
    }

    /**
     * Concrete test implementation of BaseProcessManager
     */
    private static class TestProcessManager extends BaseProcessManager {
        private final ProcessRepository repository;
        private final CommandBus commandBus;
        private final List<Runnable> transactionCallbacks;

        TestProcessManager(ProcessRepository repository, CommandBus commandBus,
                          List<Runnable> transactionCallbacks) {
            this.repository = repository;
            this.commandBus = commandBus;
            this.transactionCallbacks = transactionCallbacks;
        }

        @Override
        protected ProcessRepository getProcessRepository() {
            return repository;
        }

        @Override
        protected CommandBus getCommandBus() {
            return commandBus;
        }

        @Override
        protected void executeInTransaction(Runnable transaction) {
            // Capture transaction for test control
            transactionCallbacks.add(transaction);
        }
    }

    /**
     * Simple test process configuration: Step1 -> Step2 -> Step3
     */
    private static class SimpleTestProcessConfiguration implements ProcessConfiguration {
        @Override
        public String getProcessType() {
            return "TestProcess";
        }

        @Override
        public ProcessGraph defineProcess() {
            return process()
                .startWith(Step1Command.class)
                .then(Step2Command.class)
                .then(Step3Command.class)
                .end();
        }
    }

    /**
     * Process configuration with retry logic
     */
    private static class RetryableTestProcessConfiguration implements ProcessConfiguration {
        @Override
        public String getProcessType() {
            return "TestProcess";
        }

        @Override
        public ProcessGraph defineProcess() {
            return process()
                .startWith(Step1Command.class)
                .then(Step2Command.class)
                .then(Step3Command.class)
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
     * Process configuration with no retries
     */
    private static class NoRetryTestProcessConfiguration implements ProcessConfiguration {
        @Override
        public String getProcessType() {
            return "TestProcess";
        }

        @Override
        public ProcessGraph defineProcess() {
            return process()
                .startWith(Step1Command.class)
                .then(Step2Command.class)
                .end();
        }

        @Override
        public int getMaxRetries(String step) {
            return 0;
        }
    }

    /**
     * Process configuration with compensation
     */
    private static class CompensatingTestProcessConfiguration implements ProcessConfiguration {
        @Override
        public String getProcessType() {
            return "TestProcess";
        }

        @Override
        public ProcessGraph defineProcess() {
            return process()
                .startWith(Step1Command.class)
                    .withCompensation(CompensateStep1Command.class)
                .then(Step2Command.class)
                .end();
        }

        @Override
        public int getMaxRetries(String step) {
            return 0;
        }
    }

    @Nested
    @DisplayName("Parallel Execution Tests")
    class ParallelExecutionTests {

        public record ParallelStepCommand() implements DomainCommand {}
        public record ParallelBranch1Command() implements DomainCommand {}
        public record ParallelBranch2Command() implements DomainCommand {}
        public record ParallelBranch3Command() implements DomainCommand {}
        public record JoinStepCommand() implements DomainCommand {}
        public record AfterJoinCommand() implements DomainCommand {}

        @Test
        @DisplayName("should execute parallel step and spawn all branches")
        void testExecuteParallelStep() {
            UUID commandId1 = UUID.randomUUID();
            UUID commandId2 = UUID.randomUUID();
            UUID commandId3 = UUID.randomUUID();

            when(mockCommandBus.accept(anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(commandId1, commandId2, commandId3);

            ProcessConfiguration config = new ParallelTestProcessConfiguration();
            manager.register(config);

            // Start process
            UUID processId = manager.startProcess("ParallelProcess", "key", Map.of("initial", "data"));
            transactionCallbacks.get(0).run();

            // Verify all three branches were spawned
            verify(mockCommandBus).accept(
                eq("ParallelBranch1"),
                contains(processId.toString()),
                eq("key"),
                anyString(),
                argThat(headers -> headers.containsKey("parallelBranch"))
            );

            verify(mockCommandBus).accept(
                eq("ParallelBranch2"),
                contains(processId.toString()),
                eq("key"),
                anyString(),
                argThat(headers -> headers.containsKey("parallelBranch"))
            );

            verify(mockCommandBus).accept(
                eq("ParallelBranch3"),
                contains(processId.toString()),
                eq("key"),
                anyString(),
                argThat(headers -> headers.containsKey("parallelBranch"))
            );

            // Verify process moved to join step
            verify(mockRepository, atLeastOnce()).update(
                argThat(inst -> inst.currentStep().equals("JoinStep")),
                any(ProcessEvent.class)
            );
        }

        @Test
        @DisplayName("should handle parallel branch completion - first branch")
        void testParallelBranchCompletionFirst() {
            UUID processId = UUID.randomUUID();
            UUID commandId = UUID.randomUUID();

            // Process is at join step, waiting for parallel branches
            Map<String, Object> data = new HashMap<>();
            data.put("initial", "data");
            Map<String, String> parallelState = new HashMap<>();
            parallelState.put("ParallelBranch1", "PENDING");
            parallelState.put("ParallelBranch2", "PENDING");
            parallelState.put("ParallelBranch3", "PENDING");
            data.put("_parallel_ParallelStep", parallelState);

            ProcessInstance instance = ProcessInstance.create(
                processId,
                "ParallelProcess",
                "key",
                "JoinStep",
                data
            ).withStatus(ProcessStatus.RUNNING);

            when(mockRepository.findById(processId)).thenReturn(Optional.of(instance));

            ProcessConfiguration config = new ParallelTestProcessConfiguration();
            manager.register(config);

            // First branch completes
            CommandReply reply = CommandReply.completed(
                commandId,
                processId,
                Map.of("parallelBranch", "ParallelBranch1", "result1", "success")
            );

            // Execute
            manager.handleReply(processId, commandId, reply);
            transactionCallbacks.get(0).run();

            // Verify branch marked as COMPLETED but process still waiting
            verify(mockRepository).update(
                argThat(inst -> {
                    Object stateObj = inst.data().get("_parallel_ParallelStep");
                    if (stateObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> state = (Map<String, String>) stateObj;
                        return "COMPLETED".equals(state.get("ParallelBranch1")) &&
                               "PENDING".equals(state.get("ParallelBranch2")) &&
                               "PENDING".equals(state.get("ParallelBranch3"));
                    }
                    return false;
                }),
                argThat(event -> event instanceof ProcessEvent.StepCompleted)
            );

            // Verify no next step executed yet
            verify(mockCommandBus, never()).accept(eq("AfterJoin"), anyString(), anyString(), anyString(), anyMap());
        }

        @Test
        @DisplayName("should handle parallel branch completion - all branches complete")
        void testParallelBranchCompletionAll() {
            UUID processId = UUID.randomUUID();
            UUID commandId = UUID.randomUUID();
            UUID nextCommandId = UUID.randomUUID();

            // Process is at join step, two branches already complete
            Map<String, Object> data = new HashMap<>();
            data.put("initial", "data");
            Map<String, String> parallelState = new HashMap<>();
            parallelState.put("ParallelBranch1", "COMPLETED");
            parallelState.put("ParallelBranch2", "COMPLETED");
            parallelState.put("ParallelBranch3", "PENDING"); // Last one pending
            data.put("_parallel_ParallelStep", parallelState);

            ProcessInstance instance = ProcessInstance.create(
                processId,
                "ParallelProcess",
                "key",
                "JoinStep",
                data
            ).withStatus(ProcessStatus.RUNNING);

            when(mockRepository.findById(processId)).thenReturn(Optional.of(instance));
            when(mockCommandBus.accept(anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(nextCommandId);

            ProcessConfiguration config = new ParallelTestProcessConfiguration();
            manager.register(config);

            // Last branch completes
            CommandReply reply = CommandReply.completed(
                commandId,
                processId,
                Map.of("parallelBranch", "ParallelBranch3", "result3", "success")
            );

            // Execute
            manager.handleReply(processId, commandId, reply);
            transactionCallbacks.get(0).run();

            // Verify parallel state cleaned up
            verify(mockRepository, atLeastOnce()).update(
                argThat(inst -> !inst.data().containsKey("_parallel_ParallelStep")),
                any(ProcessEvent.class)
            );

            // Verify next step after join was executed
            verify(mockCommandBus).accept(
                eq("AfterJoin"),
                anyString(),
                eq("key"),
                anyString(),
                anyMap()
            );
        }

        @Test
        @DisplayName("should complete process when parallel branches are last steps")
        void testParallelBranchCompletionNoNextStep() {
            UUID processId = UUID.randomUUID();
            UUID commandId = UUID.randomUUID();

            // Process is at join step, two branches already complete
            Map<String, Object> data = new HashMap<>();
            Map<String, String> parallelState = new HashMap<>();
            parallelState.put("ParallelBranch1", "COMPLETED");
            parallelState.put("ParallelBranch2", "COMPLETED");
            parallelState.put("ParallelBranch3", "PENDING");
            data.put("_parallel_ParallelStep", parallelState);

            ProcessInstance instance = ProcessInstance.create(
                processId,
                "ParallelProcess",
                "key",
                "JoinStep",
                data
            ).withStatus(ProcessStatus.RUNNING);

            when(mockRepository.findById(processId)).thenReturn(Optional.of(instance));

            // Configuration with no next step after join
            ProcessConfiguration config = new ParallelEndProcessConfiguration();
            manager.register(config);

            // Last branch completes
            CommandReply reply = CommandReply.completed(
                commandId,
                processId,
                Map.of("parallelBranch", "ParallelBranch3", "result3", "success")
            );

            // Execute
            manager.handleReply(processId, commandId, reply);
            transactionCallbacks.get(0).run();

            // Verify process completed
            verify(mockRepository, atLeastOnce()).update(
                argThat(inst -> inst.status() == ProcessStatus.SUCCEEDED),
                argThat(event -> event instanceof ProcessEvent.ProcessCompleted)
            );
        }

        @Test
        @DisplayName("should handle parallel branch failure - fail fast")
        void testParallelBranchFailure() {
            UUID processId = UUID.randomUUID();
            UUID commandId = UUID.randomUUID();

            // Process is at join step, waiting for parallel branches
            Map<String, Object> data = new HashMap<>();
            Map<String, String> parallelState = new HashMap<>();
            parallelState.put("ParallelBranch1", "PENDING");
            parallelState.put("ParallelBranch2", "PENDING");
            parallelState.put("ParallelBranch3", "PENDING");
            data.put("_parallel_ParallelStep", parallelState);

            ProcessInstance instance = ProcessInstance.create(
                processId,
                "ParallelProcess",
                "key",
                "JoinStep",
                data
            ).withStatus(ProcessStatus.RUNNING);

            when(mockRepository.findById(processId)).thenReturn(Optional.of(instance));

            ProcessConfiguration config = new ParallelTestProcessConfiguration();
            manager.register(config);

            // One branch fails - reply data must include parallelBranch indicator
            Map<String, Object> failureData = new HashMap<>();
            failureData.put("parallelBranch", "ParallelBranch2");
            CommandReply reply = new CommandReply(
                commandId,
                processId,
                CommandReply.ReplyStatus.FAILED,
                failureData,
                "Branch execution failed"
            );

            // Execute
            manager.handleReply(processId, commandId, reply);
            transactionCallbacks.get(0).run();

            // Verify process failed (fail-fast approach)
            verify(mockRepository, atLeastOnce()).update(
                argThat(inst -> inst.status() == ProcessStatus.FAILED),
                argThat(event -> event instanceof ProcessEvent.ProcessFailed)
            );
        }

        @Test
        @DisplayName("should handle invalid parallel step configuration - empty branches")
        void testInvalidParallelStepEmptyBranches() {
            // Configuration with invalid parallel step (no branches)
            ProcessConfiguration config = new ProcessConfiguration() {
                @Override
                public String getProcessType() {
                    return "InvalidParallelProcess";
                }

                @Override
                public ProcessGraph defineProcess() {
                    Map<String, ProcessStep> steps = new HashMap<>();
                    steps.put("ParallelStep", new ProcessStep(
                        "ParallelStep",
                        null,
                        new ProcessStep.Terminal()
                    ));
                    // Create graph with empty parallel branches
                    return new TestProcessGraph("ParallelStep", steps, false, true, List.of(), "JoinStep");
                }
            };

            manager.register(config);

            // Attempt to start process should throw
            assertThatThrownBy(() -> {
                UUID processId = manager.startProcess("InvalidParallelProcess", "key", Map.of());
                transactionCallbacks.get(0).run();
            }).isInstanceOf(IllegalStateException.class)
              .hasMessageContaining("Invalid parallel step");
        }

        @Test
        @DisplayName("should handle invalid parallel step configuration - no join step")
        void testInvalidParallelStepNoJoin() {
            // Configuration with invalid parallel step (no join step)
            ProcessConfiguration config = new ProcessConfiguration() {
                @Override
                public String getProcessType() {
                    return "InvalidParallelProcess2";
                }

                @Override
                public ProcessGraph defineProcess() {
                    Map<String, ProcessStep> steps = new HashMap<>();
                    steps.put("ParallelStep", new ProcessStep(
                        "ParallelStep",
                        null,
                        new ProcessStep.Terminal()
                    ));
                    // Create graph with branches but no join step
                    return new TestProcessGraph("ParallelStep", steps, false, true,
                        List.of("Branch1", "Branch2"), null);
                }
            };

            manager.register(config);

            // Attempt to start process should throw
            assertThatThrownBy(() -> {
                UUID processId = manager.startProcess("InvalidParallelProcess2", "key", Map.of());
                transactionCallbacks.get(0).run();
            }).isInstanceOf(IllegalStateException.class)
              .hasMessageContaining("Invalid parallel step");
        }

        @Test
        @DisplayName("should handle parallel branch reply with missing parallelBranch in data")
        void testParallelBranchMissingInReply() {
            UUID processId = UUID.randomUUID();
            UUID commandId = UUID.randomUUID();
            UUID nextCommandId = UUID.randomUUID();

            // Process is at join step
            ProcessInstance instance = ProcessInstance.create(
                processId,
                "ParallelProcess",
                "key",
                "JoinStep",
                Map.of()
            ).withStatus(ProcessStatus.RUNNING);

            when(mockRepository.findById(processId)).thenReturn(Optional.of(instance));
            when(mockCommandBus.accept(anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(nextCommandId);

            ProcessConfiguration config = new ParallelTestProcessConfiguration();
            manager.register(config);

            // Reply without parallelBranch indicator - should be treated as sequential
            CommandReply reply = CommandReply.completed(
                commandId,
                processId,
                Map.of("result", "success") // No parallelBranch field
            );

            // Execute
            manager.handleReply(processId, commandId, reply);
            transactionCallbacks.get(0).run();

            // Should handle as sequential step completion
            verify(mockRepository, atLeastOnce()).update(
                any(ProcessInstance.class),
                any(ProcessEvent.class)
            );
        }

        @Test
        @DisplayName("should handle parallel branch completion with invalid parallel state")
        void testParallelBranchInvalidState() {
            UUID processId = UUID.randomUUID();
            UUID commandId = UUID.randomUUID();
            UUID nextCommandId = UUID.randomUUID();

            // Process data has invalid parallel state (not a Map)
            Map<String, Object> data = new HashMap<>();
            data.put("_parallel_ParallelStep", "INVALID_STRING"); // Should be a Map

            ProcessInstance instance = ProcessInstance.create(
                processId,
                "ParallelProcess",
                "key",
                "JoinStep",
                data
            ).withStatus(ProcessStatus.RUNNING);

            when(mockRepository.findById(processId)).thenReturn(Optional.of(instance));
            when(mockCommandBus.accept(anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(nextCommandId);

            ProcessConfiguration config = new ParallelTestProcessConfiguration();
            manager.register(config);

            CommandReply reply = CommandReply.completed(
                commandId,
                processId,
                Map.of("parallelBranch", "ParallelBranch1", "result", "success")
            );

            // Execute - should fall back to sequential handling
            manager.handleReply(processId, commandId, reply);
            transactionCallbacks.get(0).run();

            verify(mockRepository, atLeastOnce()).update(
                any(ProcessInstance.class),
                any(ProcessEvent.class)
            );
        }

        @Test
        @DisplayName("should handle exception during next step execution after parallel join")
        void testParallelJoinNextStepException() {
            UUID processId = UUID.randomUUID();
            UUID commandId = UUID.randomUUID();

            // Process is at join step, two branches already complete
            Map<String, Object> data = new HashMap<>();
            Map<String, String> parallelState = new HashMap<>();
            parallelState.put("ParallelBranch1", "COMPLETED");
            parallelState.put("ParallelBranch2", "COMPLETED");
            parallelState.put("ParallelBranch3", "PENDING");
            data.put("_parallel_ParallelStep", parallelState);

            ProcessInstance instance = ProcessInstance.create(
                processId,
                "ParallelProcess",
                "key",
                "JoinStep",
                data
            ).withStatus(ProcessStatus.RUNNING);

            when(mockRepository.findById(processId)).thenReturn(Optional.of(instance));
            when(mockCommandBus.accept(anyString(), anyString(), anyString(), anyString(), anyMap()))
                .thenThrow(new RuntimeException("Command bus failure"));

            ProcessConfiguration config = new ParallelTestProcessConfiguration();
            manager.register(config);

            // Last branch completes
            CommandReply reply = CommandReply.completed(
                commandId,
                processId,
                Map.of("parallelBranch", "ParallelBranch3", "result3", "success")
            );

            // Execute
            manager.handleReply(processId, commandId, reply);
            transactionCallbacks.get(0).run();

            // Verify process failed due to exception
            verify(mockRepository, atLeastOnce()).update(
                argThat(inst -> inst.status() == ProcessStatus.FAILED),
                argThat(event -> event instanceof ProcessEvent.ProcessFailed)
            );
        }
    }

    /**
     * Parallel process configuration for testing
     * ParallelStep -> [Branch1, Branch2, Branch3] -> JoinStep -> AfterJoin
     */
    private static class ParallelTestProcessConfiguration implements ProcessConfiguration {
        @Override
        public String getProcessType() {
            return "ParallelProcess";
        }

        @Override
        public ProcessGraph defineProcess() {
            return ProcessGraphBuilder.process()
                .startWith(ParallelExecutionTests.ParallelStepCommand.class)
                .thenParallel()
                    .branch(ParallelExecutionTests.ParallelBranch1Command.class)
                    .branch(ParallelExecutionTests.ParallelBranch2Command.class)
                    .branch(ParallelExecutionTests.ParallelBranch3Command.class)
                    .joinAt(ParallelExecutionTests.JoinStepCommand.class)
                .then(ParallelExecutionTests.AfterJoinCommand.class)
                .end();
        }
    }

    /**
     * Parallel process that ends after parallel execution
     * ParallelStep -> [Branch1, Branch2, Branch3] -> JoinStep (end)
     */
    private static class ParallelEndProcessConfiguration implements ProcessConfiguration {
        @Override
        public String getProcessType() {
            return "ParallelProcess";
        }

        @Override
        public ProcessGraph defineProcess() {
            return ProcessGraphBuilder.process()
                .startWith(ParallelExecutionTests.ParallelStepCommand.class)
                .thenParallel()
                    .branch(ParallelExecutionTests.ParallelBranch1Command.class)
                    .branch(ParallelExecutionTests.ParallelBranch2Command.class)
                    .branch(ParallelExecutionTests.ParallelBranch3Command.class)
                    .joinAt(ParallelExecutionTests.JoinStepCommand.class)
                .end();
        }
    }

    /**
     * Test helper ProcessGraph that supports custom parallel configuration
     */
    private static class TestProcessGraph extends ProcessGraph {
        private final boolean requiresCompensationOverride;
        private final boolean isParallelOverride;
        private final List<String> parallelBranches;
        private final String joinStep;

        TestProcessGraph(String initialStep, Map<String, ProcessStep> steps,
                        boolean requiresCompensationOverride, boolean isParallelOverride) {
            super(initialStep, steps);
            this.requiresCompensationOverride = requiresCompensationOverride;
            this.isParallelOverride = isParallelOverride;
            this.parallelBranches = List.of();
            this.joinStep = null;
        }

        TestProcessGraph(String initialStep, Map<String, ProcessStep> steps,
                        boolean requiresCompensationOverride, boolean isParallelOverride,
                        List<String> parallelBranches, String joinStep) {
            super(initialStep, steps);
            this.requiresCompensationOverride = requiresCompensationOverride;
            this.isParallelOverride = isParallelOverride;
            this.parallelBranches = parallelBranches != null ? parallelBranches : List.of();
            this.joinStep = joinStep;
        }

        @Override
        public boolean requiresCompensation(String stepName) {
            if (requiresCompensationOverride) {
                return true;
            }
            return super.requiresCompensation(stepName);
        }

        @Override
        public boolean isParallelStep(String stepName) {
            if (isParallelOverride) {
                return true;
            }
            return super.isParallelStep(stepName);
        }

        @Override
        public java.util.List<String> getParallelBranches(String stepName) {
            if (isParallelOverride) {
                return parallelBranches;
            }
            return super.getParallelBranches(stepName);
        }

        @Override
        public Optional<String> getJoinStep(String stepName) {
            if (isParallelOverride) {
                return Optional.ofNullable(joinStep);
            }
            return super.getJoinStep(stepName);
        }
    }
}
