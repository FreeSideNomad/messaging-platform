package com.acme.reliable.process;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Represents a single step in a process DAG. Can have a direct next step, conditional next steps,
 * parallel branches, or be terminal.
 */
public class ProcessStep {
  private final String stepName;
  private final String compensationStep;
  private final NextStepStrategy nextStepStrategy;

  public ProcessStep(String stepName, String compensationStep, NextStepStrategy nextStepStrategy) {
    this.stepName = stepName;
    this.compensationStep = compensationStep;
    this.nextStepStrategy = nextStepStrategy;
  }

  public String getStepName() {
    return stepName;
  }

  public Optional<String> getCompensationStep() {
    return Optional.ofNullable(compensationStep);
  }

  public Optional<String> getNextStep(Map<String, Object> data) {
    return nextStepStrategy != null ? nextStepStrategy.getNextStep(data) : Optional.empty();
  }

  /** Check if this step spawns parallel branches */
  public boolean isParallel() {
    return nextStepStrategy instanceof ParallelNext;
  }

  /** Get the list of parallel branch steps if this is a parallel step */
  public List<String> getParallelBranches() {
    if (nextStepStrategy instanceof ParallelNext parallelNext) {
      return parallelNext.getParallelBranches();
    }
    return List.of();
  }

  /** Get the join step for parallel execution */
  public Optional<String> getJoinStep() {
    if (nextStepStrategy instanceof ParallelNext parallelNext) {
      return Optional.of(parallelNext.getJoinStep());
    }
    return Optional.empty();
  }

  /** Strategy for determining the next step */
  public interface NextStepStrategy {
    Optional<String> getNextStep(Map<String, Object> data);
  }

  /** Direct next step - always goes to the same step */
  public static class DirectNext implements NextStepStrategy {
    private final String nextStep;

    public DirectNext(String nextStep) {
      this.nextStep = nextStep;
    }

    @Override
    public Optional<String> getNextStep(Map<String, Object> data) {
      return Optional.of(nextStep);
    }
  }

  /** Conditional next step - evaluates condition to decide which path to take */
  public static class ConditionalNext implements NextStepStrategy {
    private final Predicate<Map<String, Object>> condition;
    private final String trueStep;
    private final String falseStep;

    public ConditionalNext(
        Predicate<Map<String, Object>> condition, String trueStep, String falseStep) {
      this.condition = condition;
      this.trueStep = trueStep;
      this.falseStep = falseStep;
    }

    @Override
    public Optional<String> getNextStep(Map<String, Object> data) {
      return condition.test(data) ? Optional.of(trueStep) : Optional.of(falseStep);
    }
  }

  /** Terminal step - no next step */
  public static class Terminal implements NextStepStrategy {
    @Override
    public Optional<String> getNextStep(Map<String, Object> data) {
      return Optional.empty();
    }
  }

  /** Parallel execution - spawns multiple branches that must all complete before joining */
  public static class ParallelNext implements NextStepStrategy {
    private final List<String> parallelBranches;
    private final String joinStep;

    public ParallelNext(List<String> parallelBranches, String joinStep) {
      this.parallelBranches = List.copyOf(parallelBranches);
      this.joinStep = joinStep;
    }

    public List<String> getParallelBranches() {
      return parallelBranches;
    }

    public String getJoinStep() {
      return joinStep;
    }

    @Override
    public Optional<String> getNextStep(Map<String, Object> data) {
      // For parallel steps, the "next step" is the join step
      // The orchestrator should use getParallelBranches() to spawn parallel executions
      return Optional.of(joinStep);
    }
  }
}
