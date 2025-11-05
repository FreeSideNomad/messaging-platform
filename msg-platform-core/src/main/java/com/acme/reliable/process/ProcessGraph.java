package com.acme.reliable.process;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a process as a directed acyclic graph (DAG) of steps. This is constructed once during
 * process definition initialization and then navigated during process execution.
 */
public class ProcessGraph {
  private final String initialStep;
  private final Map<String, ProcessStep> steps;

  public ProcessGraph(String initialStep, Map<String, ProcessStep> steps) {
    this.initialStep = initialStep;
    this.steps = new HashMap<>(steps);
  }

  public String getInitialStep() {
    return initialStep;
  }

  public ProcessStep getStep(String stepName) {
    return steps.get(stepName);
  }

  public Optional<String> getNextStep(String currentStep, Map<String, Object> data) {
    ProcessStep step = steps.get(currentStep);
    if (step == null) {
      return Optional.empty();
    }
    return step.getNextStep(data);
  }

  public boolean requiresCompensation(String stepName) {
    ProcessStep step = steps.get(stepName);
    return step != null && step.getCompensationStep().isPresent();
  }

  public Optional<String> getCompensationStep(String stepName) {
    ProcessStep step = steps.get(stepName);
    return step != null ? step.getCompensationStep() : Optional.empty();
  }

  /** Check if a step spawns parallel execution */
  public boolean isParallelStep(String stepName) {
    ProcessStep step = steps.get(stepName);
    return step != null && step.isParallel();
  }

  /** Get the parallel branches for a parallel step */
  public java.util.List<String> getParallelBranches(String stepName) {
    ProcessStep step = steps.get(stepName);
    return step != null ? step.getParallelBranches() : java.util.List.of();
  }

  /** Get the join step for a parallel execution */
  public Optional<String> getJoinStep(String stepName) {
    ProcessStep step = steps.get(stepName);
    return step != null ? step.getJoinStep() : Optional.empty();
  }
}
