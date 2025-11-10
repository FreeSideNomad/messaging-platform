package com.acme.reliable.process;

import com.acme.reliable.command.CommandBus;
import com.acme.reliable.core.Jsons;
import com.acme.reliable.repository.ProcessRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base class for Process Manager orchestration engine. Contains all the domain logic for
 * coordinating multi-step business processes. Subclasses provide infrastructure concerns
 * (transactions, DI, lifecycle).
 */
public abstract class BaseProcessManager {

    private static final Logger LOG = LoggerFactory.getLogger(BaseProcessManager.class);

    private static final String PARALLEL_STATE_KEY = "_parallel_";
    private static final String HEADER_CORRELATION_ID = "correlationId";
    private static final String HEADER_IDEMPOTENCY_KEY = "idempotencyKey";
    private static final String HEADER_BUSINESS_KEY = "businessKey";
    private static final String HEADER_PARALLEL_BRANCH = "parallelBranch";
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final Map<String, ProcessConfiguration> configurations = new ConcurrentHashMap<>();
    private final Map<String, ProcessGraph> graphs = new ConcurrentHashMap<>();

    // Template methods - subclasses provide infrastructure dependencies
    protected abstract ProcessRepository getProcessRepository();

    protected abstract CommandBus getCommandBus();

    protected abstract void executeInTransaction(Runnable action);

    /**
     * Register a process configuration
     *
     * @throws IllegalStateException if a configuration is already registered for this process type
     */
    public void register(ProcessConfiguration config) {
        String processType = config.getProcessType();
        if (configurations.containsKey(processType)) {
            String error = "Process already registered for type: " + processType;
            LOG.error(error);
            throw new IllegalStateException(error);
        }
        configurations.put(processType, config);

        // Cache the ProcessGraph
        ProcessGraph graph = config.defineProcess();
        if (graph != null) {
            graphs.put(processType, graph);
            LOG.debug("Cached ProcessGraph for: {}", processType);
        }

        LOG.debug("Registered process: {}", processType);
    }

    /**
     * Start a new process instance
     *
     * @param processType the type of process
     * @param businessKey the business identifier (e.g., paymentId)
     * @param initialData initial process data
     * @return the process ID
     */
    public UUID startProcess(
            String processType, String businessKey, Map<String, Object> initialData) {
        ProcessGraph graph = getGraph(processType);
        ProcessConfiguration config = getConfiguration(processType);

        UUID processId = UUID.randomUUID();
        String initialStep = graph.getInitialStep();

        ProcessInstance instance =
                ProcessInstance.create(processId, processType, businessKey, initialStep, initialData);

        ProcessEvent event = new ProcessEvent.ProcessStarted(processType, businessKey, initialData);

        executeInTransaction(
                () -> {
                    getProcessRepository().insert(instance, event);

                    LOG.info(
                            "Started process: {} type={} key={} step={}",
                            processId,
                            processType,
                            businessKey,
                            initialStep);

                    // Execute first step
                    try {
                        executeStep(instance, config);
                    } catch (Exception e) {
                        LOG.error("Failed to execute initial step for process: {}", processId, e);
                        ProcessInstance failed = instance.withStatus(ProcessStatus.FAILED);
                        ProcessEvent failEvent =
                                new ProcessEvent.ProcessFailed("Failed to start: " + e.getMessage());
                        getProcessRepository().update(failed, failEvent);
                        throw e;
                    }
                });

        return processId;
    }

    /**
     * Handle a command reply
     *
     * @param correlationId the process ID (used as correlationId)
     * @param commandId     the command that completed
     * @param reply         the reply status and data
     */
    public void handleReply(UUID correlationId, UUID commandId, CommandReply reply) {
        executeInTransaction(
                () -> {
                    Optional<ProcessInstance> maybeInstance = getProcessRepository().findById(correlationId);

                    if (maybeInstance.isEmpty()) {
                        LOG.warn("Received reply for unknown process: {} command={}", correlationId, commandId);
                        return;
                    }

                    ProcessInstance instance = maybeInstance.get();
                    ProcessConfiguration definition = getConfiguration(instance.processType());

                    LOG.debug(
                            "Handling reply for process: {} command={} status={}",
                            instance.processId(),
                            commandId,
                            reply.status());

                    switch (reply.status()) {
                        case COMPLETED -> handleStepCompleted(instance, definition, commandId, reply);
                        case FAILED -> handleStepFailed(instance, definition, commandId, reply);
                        case TIMED_OUT -> handleStepTimedOut(instance, definition, commandId, reply);
                    }
                });
    }

    /**
     * Execute a process step by sending a command Handles both sequential and parallel steps
     */
    private void executeStep(ProcessInstance instance, ProcessConfiguration definition) {
        String step = instance.currentStep();
        ProcessGraph graph = graphs.get(instance.processType());

        // Check if this is a parallel step
        if (graph != null && graph.isParallelStep(step)) {
            executeParallelStep(instance, definition, graph, step);
        } else {
            executeSequentialStep(instance, step);
        }
    }

    /**
     * Execute a sequential (non-parallel) step
     */
    private void executeSequentialStep(ProcessInstance instance, String step) {
        String idempotencyKey = instance.processId() + ":" + step;

        // Prepare command payload
        Map<String, String> headers =
                Map.of(
                        HEADER_CORRELATION_ID,
                        instance.processId().toString(),
                        HEADER_IDEMPOTENCY_KEY,
                        idempotencyKey);

        // Build command payload from process data
        Map<String, Object> commandData = new HashMap<>(instance.data());
        commandData.put(HEADER_BUSINESS_KEY, instance.businessKey());
        commandData.put("step", step);

        String payload = Jsons.toJson(commandData);

        // Send command via CommandBus
        UUID commandId =
                getCommandBus().accept(step, idempotencyKey, instance.businessKey(), payload, headers);

        // Log step started
        ProcessEvent event = new ProcessEvent.StepStarted(step, commandId.toString());
        ProcessInstance updated = instance.withStatus(ProcessStatus.RUNNING);
        getProcessRepository().update(updated, event);

        LOG.info("Executed step: process={} step={} command={}", instance.processId(), step, commandId);
    }

    /**
     * Execute a parallel step - spawn multiple commands
     */
    private void executeParallelStep(
            ProcessInstance instance, ProcessConfiguration definition, ProcessGraph graph, String step) {
        java.util.List<String> branches = graph.getParallelBranches(step);
        String joinStep = graph.getJoinStep(step).orElse(null);

        if (branches.isEmpty() || joinStep == null) {
            LOG.error(
                    "Invalid parallel step configuration: step={} branches={} joinStep={}",
                    step,
                    branches.size(),
                    joinStep);
            throw new IllegalStateException("Invalid parallel step: " + step);
        }

        LOG.info(
                "Executing parallel step: process={} step={} branches={} joinStep={}",
                instance.processId(),
                step,
                branches.size(),
                joinStep);

        // Initialize parallel state tracking
        Map<String, Object> newData = new HashMap<>(instance.data());
        Map<String, String> parallelState = new HashMap<>();
        for (String branch : branches) {
            parallelState.put(branch, "PENDING");
        }
        newData.put(PARALLEL_STATE_KEY + step, parallelState);

        // Update instance to move to join step (will wait for all branches)
        ProcessInstance updated = instance.update(ProcessStatus.RUNNING, joinStep, newData, 0);
        ProcessEvent event = new ProcessEvent.StepStarted(step, "PARALLEL:" + branches.size());
        getProcessRepository().update(updated, event);

        // Spawn all parallel branch commands
        for (String branch : branches) {
            String idempotencyKey = instance.processId() + ":" + branch;

            Map<String, String> headers =
                    Map.of(
                            HEADER_CORRELATION_ID,
                            instance.processId().toString(),
                            HEADER_IDEMPOTENCY_KEY,
                            idempotencyKey,
                            HEADER_PARALLEL_BRANCH,
                            branch,
                            "parentStep",
                            step);

            Map<String, Object> commandData = new HashMap<>(instance.data());
            commandData.put(HEADER_BUSINESS_KEY, instance.businessKey());
            commandData.put("step", branch);
            commandData.put("parallelBranch", branch);

            String payload = Jsons.toJson(commandData);

            UUID commandId =
                    getCommandBus().accept(branch, idempotencyKey, instance.businessKey(), payload, headers);

            LOG.info(
                    "Spawned parallel branch: process={} parent={} branch={} command={}",
                    instance.processId(),
                    step,
                    branch,
                    commandId);
        }
    }

    /**
     * Handle successful step completion
     */
    private void handleStepCompleted(
            ProcessInstance instance,
            ProcessConfiguration definition,
            UUID commandId,
            CommandReply reply) {
        // Check if this is a parallel branch completion
        String completedBranch = extractParallelBranchFromReply(reply);
        if (completedBranch != null) {
            handleParallelBranchCompleted(instance, definition, commandId, reply, completedBranch);
            return;
        }

        // Regular sequential step completion
        handleSequentialStepCompleted(instance, definition, commandId, reply);
    }

    /**
     * Extract parallel branch name from command reply (if this was a parallel branch)
     */
    private String extractParallelBranchFromReply(CommandReply reply) {
        // Check reply data for parallel branch indicator
        Object branchObj = reply.data().get("parallelBranch");
        return branchObj instanceof String ? (String) branchObj : null;
    }

    /**
     * Handle completion of a parallel branch
     */
    private void handleParallelBranchCompleted(
            ProcessInstance instance,
            ProcessConfiguration definition,
            UUID commandId,
            CommandReply reply,
            String completedBranch) {
        // Merge reply data into process data
        Map<String, Object> newData = new HashMap<>(instance.data());
        newData.putAll(reply.data());

        // Find the parent parallel step by looking for parallel state tracking
        String parentStep = findParentParallelStep(newData, completedBranch);
        if (parentStep == null) {
            LOG.error("Could not find parent parallel step for branch: {}", completedBranch);
            handleSequentialStepCompleted(instance, definition, commandId, reply);
            return;
        }

        String parallelStateKey = PARALLEL_STATE_KEY + parentStep;
        Object parallelStateObj = newData.get(parallelStateKey);

        if (!(parallelStateObj instanceof Map)) {
            LOG.error("Invalid parallel state for parent step: {}", parentStep);
            handleSequentialStepCompleted(instance, definition, commandId, reply);
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, String> parallelState = (Map<String, String>) parallelStateObj;

        // Update branch state to COMPLETED
        parallelState.put(completedBranch, STATUS_COMPLETED);
        newData.put(parallelStateKey, parallelState);

        ProcessEvent event =
                new ProcessEvent.StepCompleted(completedBranch, commandId.toString(), reply.data());

        // Check if all branches are complete
        boolean allComplete = parallelState.values().stream().allMatch(STATUS_COMPLETED::equals);

        if (!allComplete) {
            // Wait for other branches - just update state
            ProcessInstance updated =
                    instance.update(ProcessStatus.RUNNING, instance.currentStep(), newData, 0);
            getProcessRepository().update(updated, event);

            long completedCount =
                    parallelState.values().stream().filter(STATUS_COMPLETED::equals).count();

            LOG.info(
                    "Parallel branch completed, waiting for others: process={} branch={} completed={}/{}",
                    instance.processId(),
                    completedBranch,
                    completedCount,
                    parallelState.size());
            return;
        }

        // All branches complete - proceed from join step
        LOG.info(
                "All parallel branches completed: process={} parentStep={} branches={}",
                instance.processId(),
                parentStep,
                parallelState.size());

        // Clean up parallel state
        newData.remove(parallelStateKey);

        ProcessInstance updated =
                instance.update(ProcessStatus.RUNNING, instance.currentStep(), newData, 0);
        getProcessRepository().update(updated, event);

        // Determine next step from join point
        Optional<String> nextStep =
                getGraph(instance.processType()).getNextStep(instance.currentStep(), newData);

        if (nextStep.isPresent()) {
            ProcessInstance next = updated.update(ProcessStatus.RUNNING, nextStep.get(), newData, 0);
            ProcessEvent nextEvent = new ProcessEvent.StepStarted(nextStep.get(), "after-parallel-join");
            getProcessRepository().update(next, nextEvent);

            LOG.info(
                    "Moving to next step after parallel join: process={} from={} to={}",
                    instance.processId(),
                    instance.currentStep(),
                    nextStep.get());

            try {
                executeStep(next, definition);
            } catch (Exception e) {
                LOG.error("Failed to execute next step after parallel join: {}", instance.processId(), e);
                handleStepExecutionFailure(next, definition, e.getMessage());
            }
        } else {
            // Process complete
            ProcessInstance completed =
                    updated.update(ProcessStatus.SUCCEEDED, instance.currentStep(), newData, 0);
            ProcessEvent completeEvent =
                    new ProcessEvent.ProcessCompleted("All steps completed successfully");
            getProcessRepository().update(completed, completeEvent);

            LOG.info(
                    "Process completed successfully after parallel execution: {} type={} key={}",
                    instance.processId(),
                    instance.processType(),
                    instance.businessKey());
        }
    }

    /**
     * Find the parent parallel step by checking which parallel state contains this branch
     */
    private String findParentParallelStep(Map<String, Object> data, String branchName) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getKey().startsWith(PARALLEL_STATE_KEY)) {
                if (entry.getValue() instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> parallelState = (Map<String, String>) entry.getValue();
                    if (parallelState.containsKey(branchName)) {
                        return entry.getKey().substring(PARALLEL_STATE_KEY.length());
                    }
                }
            }
        }
        return null;
    }

    /**
     * Handle sequential (non-parallel) step completion
     */
    private void handleSequentialStepCompleted(
            ProcessInstance instance,
            ProcessConfiguration definition,
            UUID commandId,
            CommandReply reply) {
        // Merge reply data into process data
        Map<String, Object> newData = new HashMap<>(instance.data());
        newData.putAll(reply.data());

        ProcessEvent event =
                new ProcessEvent.StepCompleted(instance.currentStep(), commandId.toString(), reply.data());

        // Determine next step
        Optional<String> nextStep =
                getGraph(instance.processType()).getNextStep(instance.currentStep(), newData);

        if (nextStep.isPresent()) {
            // Continue to next step
            ProcessInstance updated = instance.update(ProcessStatus.RUNNING, nextStep.get(), newData, 0);

            getProcessRepository().update(updated, event);

            LOG.info(
                    "Process step completed, moving to next: process={} from={} to={}",
                    instance.processId(),
                    instance.currentStep(),
                    nextStep.get());

            // Execute next step
            try {
                executeStep(updated, definition);
            } catch (Exception e) {
                LOG.error("Failed to execute next step for process: {}", instance.processId(), e);
                handleStepExecutionFailure(updated, definition, e.getMessage());
            }
        } else {
            // Process complete
            ProcessInstance completed =
                    instance.update(ProcessStatus.SUCCEEDED, instance.currentStep(), newData, 0);

            ProcessEvent completeEvent =
                    new ProcessEvent.ProcessCompleted("All steps completed successfully");
            getProcessRepository().update(completed, completeEvent);

            LOG.info(
                    "Process completed successfully: {} type={} key={}",
                    instance.processId(),
                    instance.processType(),
                    instance.businessKey());
        }
    }

    /**
     * Handle step failure
     */
    private void handleStepFailed(
            ProcessInstance instance,
            ProcessConfiguration definition,
            UUID commandId,
            CommandReply reply) {
        // Check if this is a parallel branch failure
        String failedBranch = extractParallelBranchFromReply(reply);
        if (failedBranch != null) {
            handleParallelBranchFailed(instance, definition, commandId, reply, failedBranch);
            return;
        }

        // Regular sequential step failure
        handleSequentialStepFailed(instance, definition, commandId, reply);
    }

    /**
     * Handle parallel branch failure - fail-fast approach
     */
    private void handleParallelBranchFailed(
            ProcessInstance instance,
            ProcessConfiguration definition,
            UUID commandId,
            CommandReply reply,
            String failedBranch) {
        String error = reply.error();

        LOG.error(
                "Parallel branch failed: process={} branch={} error={}",
                instance.processId(),
                failedBranch,
                error);

        // Fail-fast: any parallel branch failure fails the entire process
        ProcessEvent event =
                new ProcessEvent.StepFailed(failedBranch, commandId.toString(), error, false);

        handlePermanentFailure(
                instance, definition, event, "Parallel branch failed: " + failedBranch + " - " + error);
    }

    /**
     * Handle sequential step failure
     */
    private void handleSequentialStepFailed(
            ProcessInstance instance,
            ProcessConfiguration definition,
            UUID commandId,
            CommandReply reply) {
        String error = reply.error();
        boolean retryable = definition.isRetryable(instance.currentStep(), error);
        int maxRetries = definition.getMaxRetries(instance.currentStep());

        ProcessEvent event =
                new ProcessEvent.StepFailed(instance.currentStep(), commandId.toString(), error, retryable);

        if (retryable && instance.retries() < maxRetries) {
            // Retry the step
            int newRetries = instance.retries() + 1;
            ProcessInstance updated = instance.withRetries(newRetries);
            getProcessRepository().update(updated, event);

            LOG.warn(
                    "Step failed, retrying: process={} step={} attempt={}/{}",
                    instance.processId(),
                    instance.currentStep(),
                    newRetries,
                    maxRetries);

            // Schedule retry with backoff
            long delayMs = definition.getRetryDelay(instance.currentStep(), newRetries - 1);
            try {
                Thread.sleep(delayMs); // Simple delay - could use scheduler for production
                executeStep(updated, definition);
            } catch (InterruptedException e) {
                // Thread was interrupted - restore interrupted status and handle as permanent failure
                Thread.currentThread().interrupt();
                LOG.error("Retry interrupted for process: {}", instance.processId(), e);
                handlePermanentFailure(updated, definition, event, "Retry interrupted");
            } catch (Exception e) {
                LOG.error("Failed to retry step for process: {}", instance.processId(), e);
                handlePermanentFailure(updated, definition, event, "Retry failed: " + e.getMessage());
            }
        } else {
            // Permanent failure
            LOG.error(
                    "Step failed permanently: process={} step={} error={}",
                    instance.processId(),
                    instance.currentStep(),
                    error);

            handlePermanentFailure(instance, definition, event, error);
        }
    }

    /**
     * Handle step timeout
     */
    private void handleStepTimedOut(
            ProcessInstance instance,
            ProcessConfiguration definition,
            UUID commandId,
            CommandReply reply) {
        String error = "Timeout: " + reply.error();

        ProcessEvent event =
                new ProcessEvent.StepTimedOut(instance.currentStep(), commandId.toString(), error);

        LOG.error("Step timed out: process={} step={}", instance.processId(), instance.currentStep());

        // Treat timeout as permanent failure
        handlePermanentFailure(instance, definition, event, error);
    }

    /**
     * Handle permanent step failure - check if compensation is needed
     */
    private void handlePermanentFailure(
            ProcessInstance instance,
            ProcessConfiguration definition,
            ProcessEvent triggerEvent,
            String error) {
        if (getGraph(instance.processType()).requiresCompensation(instance.currentStep())) {
            LOG.info(
                    "Starting compensation for process: {} step={}",
                    instance.processId(),
                    instance.currentStep());

            ProcessInstance compensating = instance.withStatus(ProcessStatus.COMPENSATING);
            getProcessRepository().update(compensating, triggerEvent);

            startCompensation(compensating, definition);
        } else {
            ProcessInstance failed = instance.withStatus(ProcessStatus.FAILED);
            ProcessEvent failEvent = new ProcessEvent.ProcessFailed(error);
            getProcessRepository().update(failed, failEvent);

            LOG.error(
                    "Process failed without compensation: {} type={} error={}",
                    instance.processId(),
                    instance.processType(),
                    error);
        }
    }

    /**
     * Start compensation flow
     */
    private void startCompensation(ProcessInstance instance, ProcessConfiguration definition) {
        Optional<String> compensationStep =
                getGraph(instance.processType()).getCompensationStep(instance.currentStep());

        if (compensationStep.isPresent()) {
            String step = compensationStep.get();
            String idempotencyKey = instance.processId() + ":COMPENSATE:" + step;

            Map<String, String> headers =
                    Map.of(
                            "correlationId", instance.processId().toString(), "idempotencyKey", idempotencyKey);

            Map<String, Object> commandData = new HashMap<>(instance.data());
            commandData.put("businessKey", instance.businessKey());
            commandData.put("step", step);
            commandData.put("compensating", true);

            String payload = Jsons.toJson(commandData);

            UUID commandId =
                    getCommandBus().accept(step, idempotencyKey, instance.businessKey(), payload, headers);

            ProcessEvent event = new ProcessEvent.CompensationStarted(step, commandId.toString());
            getProcessRepository().update(instance, event);

            LOG.info(
                    "Started compensation step: process={} step={} command={}",
                    instance.processId(),
                    step,
                    commandId);
        } else {
            // No compensation step - mark as compensated
            ProcessInstance compensated = instance.withStatus(ProcessStatus.COMPENSATED);
            ProcessEvent event = new ProcessEvent.ProcessCompleted("Compensated (no compensation step)");
            getProcessRepository().update(compensated, event);

            LOG.info("Process compensated (no action needed): {}", instance.processId());
        }
    }

    /**
     * Handle step execution failure (exception thrown during execution)
     */
    private void handleStepExecutionFailure(
            ProcessInstance instance, ProcessConfiguration definition, String error) {
        ProcessEvent event = new ProcessEvent.StepFailed(instance.currentStep(), "N/A", error, false);

        handlePermanentFailure(instance, definition, event, error);
    }

    /**
     * Get process definition by type
     */
    protected ProcessConfiguration getConfiguration(String processType) {
        ProcessConfiguration config = configurations.get(processType);
        if (config == null) {
            throw new IllegalArgumentException("Unknown process type: " + processType);
        }
        return config;
    }

    protected ProcessGraph getGraph(String processType) {
        ProcessGraph graph = graphs.get(processType);
        if (graph == null) {
            throw new IllegalArgumentException("Unknown process type: " + processType);
        }
        return graph;
    }
}
