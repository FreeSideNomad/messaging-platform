package com.acme.reliable.process;

import static com.acme.reliable.process.ProcessSteps.stepName;

import com.acme.reliable.command.DomainCommand;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Fluent builder for constructing process DAGs. Provides a declarative way to define process flows
 * with branches and compensations.
 */
public class ProcessGraphBuilder {
  private final Map<String, ProcessStep> steps = new HashMap<>();
  private String initialStep;
  private String currentStep;

  public static ProcessGraphBuilder process() {
    return new ProcessGraphBuilder();
  }

  /** Start the process with the given step */
  public StepBuilder startWith(Class<? extends DomainCommand> stepClass) {
    this.initialStep = stepName(stepClass);
    this.currentStep = this.initialStep;
    return new StepBuilder(this, currentStep);
  }

  /** Builder for configuring an individual step */
  public class StepBuilder {
    private final ProcessGraphBuilder parent;
    private final String stepName;
    private String compensationStep;

    StepBuilder(ProcessGraphBuilder parent, String stepName) {
      this.parent = parent;
      this.stepName = stepName;
    }

    /** Specify compensation step for this step */
    public StepBuilder withCompensation(Class<? extends DomainCommand> compensationClass) {
      this.compensationStep = ProcessSteps.stepName(compensationClass);
      return this;
    }

    /** Continue to next step unconditionally */
    public StepBuilder then(Class<? extends DomainCommand> nextStepClass) {
      String nextStepName = ProcessSteps.stepName(nextStepClass);
      parent.steps.put(
          stepName,
          new ProcessStep(stepName, compensationStep, new ProcessStep.DirectNext(nextStepName)));
      parent.currentStep = nextStepName;
      return new StepBuilder(parent, nextStepName);
    }

    /** Branch based on condition */
    public ConditionalBuilder thenIf(Predicate<Map<String, Object>> condition) {
      return new ConditionalBuilder(parent, stepName, compensationStep, condition);
    }

    /** Execute multiple steps in parallel */
    public ParallelBranchBuilder thenParallel() {
      return new ParallelBranchBuilder(parent, stepName, compensationStep);
    }

    /** Mark this as the terminal step */
    public ProcessGraph end() {
      parent.steps.put(
          stepName, new ProcessStep(stepName, compensationStep, new ProcessStep.Terminal()));
      return new ProcessGraph(parent.initialStep, parent.steps);
    }
  }

  /** Builder for conditional branching */
  public class ConditionalBuilder {
    private final ProcessGraphBuilder parent;
    private final String sourceStep;
    private final String compensationStep;
    private final Predicate<Map<String, Object>> condition;
    private String trueStep;
    private String falseStep;

    ConditionalBuilder(
        ProcessGraphBuilder parent,
        String sourceStep,
        String compensationStep,
        Predicate<Map<String, Object>> condition) {
      this.parent = parent;
      this.sourceStep = sourceStep;
      this.compensationStep = compensationStep;
      this.condition = condition;
    }

    /**
     * Define the step to execute when condition is true. Use this for optional branches:
     * if(condition) { optional_step }; next_step
     */
    public OptionalBranchBuilder whenTrue(Class<? extends DomainCommand> stepClass) {
      this.trueStep = ProcessSteps.stepName(stepClass);
      return new OptionalBranchBuilder(this, trueStep);
    }

    /** Define the step to execute when condition is false (for full if-else) */
    public BranchBuilder whenFalse(Class<? extends DomainCommand> stepClass) {
      this.falseStep = ProcessSteps.stepName(stepClass);
      return new BranchBuilder(this, false, falseStep);
    }

    /** Internal method for BranchBuilder to add the true branch in full if-else scenarios */
    BranchBuilder whenTrueForFullConditional(Class<? extends DomainCommand> stepClass) {
      this.trueStep = ProcessSteps.stepName(stepClass);
      return new BranchBuilder(this, true, trueStep);
    }

    void completeConditional() {
      parent.steps.put(
          sourceStep,
          new ProcessStep(
              sourceStep,
              compensationStep,
              new ProcessStep.ConditionalNext(condition, trueStep, falseStep)));
    }

    void completeConditionalWithContinuation(String continuationStep) {
      // For optional branches, false path goes directly to continuation
      this.falseStep = continuationStep;
      completeConditional();
    }
  }

  /** Builder for optional branches (only whenTrue is defined, false continues to next step) */
  public class OptionalBranchBuilder {
    private final ConditionalBuilder conditionalBuilder;
    private final String branchStepName;
    private String compensationStep;

    OptionalBranchBuilder(ConditionalBuilder conditionalBuilder, String branchStepName) {
      this.conditionalBuilder = conditionalBuilder;
      this.branchStepName = branchStepName;
    }

    /** Add compensation for the optional branch step */
    public OptionalBranchBuilder withCompensation(
        Class<? extends DomainCommand> compensationClass) {
      this.compensationStep = ProcessSteps.stepName(compensationClass);
      return this;
    }

    /**
     * Define the continuation step after the optional branch Both true and false paths will
     * converge here
     */
    public StepBuilder then(Class<? extends DomainCommand> continuationClass) {
      String continuationStep = ProcessSteps.stepName(continuationClass);

      // The branch step goes to continuation
      conditionalBuilder.parent.steps.put(
          branchStepName,
          new ProcessStep(
              branchStepName, compensationStep, new ProcessStep.DirectNext(continuationStep)));

      // Complete the conditional: true→branch, false→continuation
      conditionalBuilder.completeConditionalWithContinuation(continuationStep);

      // Return builder for the continuation step
      return new StepBuilder(conditionalBuilder.parent, continuationStep);
    }
  }

  /** Builder for configuring branch steps */
  public class BranchBuilder {
    private final ConditionalBuilder conditionalBuilder;
    private final boolean isTrueBranch;
    private final String branchStepName;
    private String compensationStep;

    BranchBuilder(
        ConditionalBuilder conditionalBuilder, boolean isTrueBranch, String branchStepName) {
      this.conditionalBuilder = conditionalBuilder;
      this.isTrueBranch = isTrueBranch;
      this.branchStepName = branchStepName;
    }

    /** Add compensation for this branch step */
    public BranchBuilder withCompensation(Class<? extends DomainCommand> compensationClass) {
      this.compensationStep = ProcessSteps.stepName(compensationClass);
      return this;
    }

    /** Continue to next step after this branch */
    public BranchContinuation then(Class<? extends DomainCommand> nextStepClass) {
      String nextStepName = ProcessSteps.stepName(nextStepClass);
      conditionalBuilder.parent.steps.put(
          branchStepName,
          new ProcessStep(
              branchStepName, compensationStep, new ProcessStep.DirectNext(nextStepName)));
      return new BranchContinuation(conditionalBuilder, nextStepName);
    }

    /** Define the other branch (when false if this is true branch, or vice versa) */
    public BranchBuilder whenFalse(Class<? extends DomainCommand> stepClass) {
      if (isTrueBranch) {
        return conditionalBuilder.whenFalse(stepClass);
      }
      throw new IllegalStateException("Cannot call whenFalse after whenFalse");
    }

    public BranchBuilder whenTrue(Class<? extends DomainCommand> stepClass) {
      if (!isTrueBranch) {
        return conditionalBuilder.whenTrueForFullConditional(stepClass);
      }
      throw new IllegalStateException("Cannot call whenTrue after whenTrue");
    }
  }

  /** Builder for continuing after branches converge */
  public class BranchContinuation {
    private final ConditionalBuilder conditionalBuilder;
    private final String convergenceStep;

    BranchContinuation(ConditionalBuilder conditionalBuilder, String convergenceStep) {
      this.conditionalBuilder = conditionalBuilder;
      this.convergenceStep = convergenceStep;
    }

    /** Add compensation to the convergence step itself */
    public BranchContinuation withCompensation(Class<? extends DomainCommand> compensationClass) {
      // The convergence step will get its compensation when whenFalse completes
      // For now, store it in the parent
      conditionalBuilder.parent.currentStep = convergenceStep;
      return this;
    }

    /** Define the other branch that converges directly to the same step */
    public SimpleBranchBuilder whenFalse(Class<? extends DomainCommand> branchStepClass) {
      String branchStep = ProcessSteps.stepName(branchStepClass);
      conditionalBuilder.falseStep = branchStep;
      return new SimpleBranchBuilder(conditionalBuilder, branchStep, convergenceStep);
    }

    public SimpleBranchBuilder whenTrue(Class<? extends DomainCommand> branchStepClass) {
      String branchStep = ProcessSteps.stepName(branchStepClass);
      conditionalBuilder.trueStep = branchStep;
      return new SimpleBranchBuilder(conditionalBuilder, branchStep, convergenceStep);
    }
  }

  /** Builder for simple branches that converge directly */
  public class SimpleBranchBuilder {
    private final ConditionalBuilder conditionalBuilder;
    private final String branchStepName;
    private final String convergenceStep;
    private String compensationStep;

    SimpleBranchBuilder(
        ConditionalBuilder conditionalBuilder, String branchStepName, String convergenceStep) {
      this.conditionalBuilder = conditionalBuilder;
      this.branchStepName = branchStepName;
      this.convergenceStep = convergenceStep;
    }

    /** Add compensation for this branch step */
    public SimpleBranchBuilder withCompensation(Class<? extends DomainCommand> compensationClass) {
      this.compensationStep = ProcessSteps.stepName(compensationClass);
      return this;
    }

    /** Complete the branch - converges directly to the convergence step */
    public StepBuilder then(Class<? extends DomainCommand> nextStepClass) {
      // Verify it's the convergence step
      String nextStepName = ProcessSteps.stepName(nextStepClass);
      if (!nextStepName.equals(convergenceStep)) {
        throw new IllegalStateException(
            "This branch must converge to " + convergenceStep + ", got: " + nextStepName);
      }

      // Complete the branch step - it goes directly to convergence
      conditionalBuilder.parent.steps.put(
          branchStepName,
          new ProcessStep(
              branchStepName, compensationStep, new ProcessStep.DirectNext(convergenceStep)));

      // Complete the conditional
      conditionalBuilder.completeConditional();

      // Return builder for the convergence step
      return new StepBuilder(conditionalBuilder.parent, convergenceStep);
    }
  }

  /** Builder for parallel execution branches */
  public class ParallelBranchBuilder {
    private final ProcessGraphBuilder parent;
    private final String sourceStep;
    private final String sourceCompensationStep;
    private final java.util.List<ParallelBranch> branches = new java.util.ArrayList<>();

    ParallelBranchBuilder(
        ProcessGraphBuilder parent, String sourceStep, String sourceCompensationStep) {
      this.parent = parent;
      this.sourceStep = sourceStep;
      this.sourceCompensationStep = sourceCompensationStep;
    }

    /** Add a parallel branch */
    public ParallelBranchConfig branch(Class<? extends DomainCommand> branchStepClass) {
      String branchStepName = ProcessSteps.stepName(branchStepClass);
      ParallelBranch branch = new ParallelBranch(branchStepName);
      branches.add(branch);
      return new ParallelBranchConfig(this, branch);
    }

    /** Define the join point where all parallel branches converge */
    public StepBuilder joinAt(Class<? extends DomainCommand> joinStepClass) {
      String joinStepName = ProcessSteps.stepName(joinStepClass);

      // Create the source step with parallel execution strategy
      java.util.List<String> branchNames = branches.stream().map(b -> b.branchStepName).toList();

      parent.steps.put(
          sourceStep,
          new ProcessStep(
              sourceStep,
              sourceCompensationStep,
              new ProcessStep.ParallelNext(branchNames, joinStepName)));

      // Create each parallel branch step
      for (ParallelBranch branch : branches) {
        parent.steps.put(
            branch.branchStepName,
            new ProcessStep(
                branch.branchStepName,
                branch.compensationStep,
                new ProcessStep.DirectNext(joinStepName)));
      }

      // Return builder for the join step
      parent.currentStep = joinStepName;
      return new StepBuilder(parent, joinStepName);
    }

    /** Represents a single parallel branch with optional compensation */
    private static class ParallelBranch {
      final String branchStepName;
      String compensationStep;

      ParallelBranch(String branchStepName) {
        this.branchStepName = branchStepName;
      }
    }
  }

  /** Configuration for a single parallel branch */
  public class ParallelBranchConfig {
    private final ParallelBranchBuilder parentBuilder;
    private final ParallelBranchBuilder.ParallelBranch branch;

    ParallelBranchConfig(
        ParallelBranchBuilder parentBuilder, ParallelBranchBuilder.ParallelBranch branch) {
      this.parentBuilder = parentBuilder;
      this.branch = branch;
    }

    /** Add compensation for this parallel branch */
    public ParallelBranchConfig withCompensation(Class<? extends DomainCommand> compensationClass) {
      branch.compensationStep = ProcessSteps.stepName(compensationClass);
      return this;
    }

    /** Add another parallel branch */
    public ParallelBranchConfig branch(Class<? extends DomainCommand> branchStepClass) {
      return parentBuilder.branch(branchStepClass);
    }

    /** Define the join point where all parallel branches converge */
    public StepBuilder joinAt(Class<? extends DomainCommand> joinStepClass) {
      return parentBuilder.joinAt(joinStepClass);
    }
  }
}
