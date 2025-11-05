package com.acme.reliable.process;

import static com.acme.reliable.process.ProcessGraphBuilder.process;
import static org.junit.jupiter.api.Assertions.*;

import com.acme.reliable.command.DomainCommand;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Comprehensive tests for ProcessGraphBuilder covering all branching patterns */
class ProcessGraphBuilderTest {

  // Test command classes
  public record Step1Command() implements DomainCommand {}

  public record Step2Command() implements DomainCommand {}

  public record Step3Command() implements DomainCommand {}

  public record Step4Command() implements DomainCommand {}

  public record CompensateStep1Command() implements DomainCommand {}

  public record CompensateStep2Command() implements DomainCommand {}

  public record CompensateStep3Command() implements DomainCommand {}

  public record TrueBranchCommand() implements DomainCommand {}

  public record FalseBranchCommand() implements DomainCommand {}

  public record CompensateTrueCommand() implements DomainCommand {}

  public record CompensateFalseCommand() implements DomainCommand {}

  public record OptionalStepCommand() implements DomainCommand {}

  public record CompensateOptionalCommand() implements DomainCommand {}

  public record ContinuationCommand() implements DomainCommand {}

  public record ParallelStep1Command() implements DomainCommand {}

  public record ParallelStep2Command() implements DomainCommand {}

  public record ParallelStep3Command() implements DomainCommand {}

  @Test
  void testSimpleLinearProcess() {
    // Given: A simple linear process: Step1 -> Step2 -> Step3
    ProcessGraph graph =
        process()
            .startWith(Step1Command.class)
            .then(Step2Command.class)
            .then(Step3Command.class)
            .end();

    // Then: Initial step is correct
    assertEquals("Step1", graph.getInitialStep());

    // And: Navigation follows linear path
    Map<String, Object> data = new HashMap<>();
    assertEquals(Optional.of("Step2"), graph.getNextStep("Step1", data));
    assertEquals(Optional.of("Step3"), graph.getNextStep("Step2", data));
    assertEquals(Optional.empty(), graph.getNextStep("Step3", data));
  }

  @Test
  void testLinearProcessWithCompensation() {
    // Given: Linear process with compensation at each step
    ProcessGraph graph =
        process()
            .startWith(Step1Command.class)
            .withCompensation(CompensateStep1Command.class)
            .then(Step2Command.class)
            .withCompensation(CompensateStep2Command.class)
            .then(Step3Command.class)
            .withCompensation(CompensateStep3Command.class)
            .end();

    // Then: Compensation is correctly defined
    assertTrue(graph.requiresCompensation("Step1"));
    assertTrue(graph.requiresCompensation("Step2"));
    assertTrue(graph.requiresCompensation("Step3"));

    assertEquals(Optional.of("CompensateStep1"), graph.getCompensationStep("Step1"));
    assertEquals(Optional.of("CompensateStep2"), graph.getCompensationStep("Step2"));
    assertEquals(Optional.of("CompensateStep3"), graph.getCompensationStep("Step3"));
  }

  @Test
  void testOptionalBranchPattern_TrueCondition() {
    // Given: Process with optional step when condition is true
    // Step1 -> if(flag) { OptionalStep } -> Continuation -> Step4
    ProcessGraph graph =
        process()
            .startWith(Step1Command.class)
            .withCompensation(CompensateStep1Command.class)
            .thenIf(data -> (Boolean) data.getOrDefault("flag", false))
            .whenTrue(OptionalStepCommand.class)
            .withCompensation(CompensateOptionalCommand.class)
            .then(ContinuationCommand.class)
            .then(Step4Command.class)
            .end();

    // When: Condition is true
    Map<String, Object> dataTrue = Map.of("flag", true);

    // Then: Should go through optional step
    assertEquals(Optional.of("OptionalStep"), graph.getNextStep("Step1", dataTrue));
    assertEquals(Optional.of("Continuation"), graph.getNextStep("OptionalStep", dataTrue));
    assertEquals(Optional.of("Step4"), graph.getNextStep("Continuation", dataTrue));

    // And: Optional step has compensation
    assertTrue(graph.requiresCompensation("OptionalStep"));
    assertEquals(Optional.of("CompensateOptional"), graph.getCompensationStep("OptionalStep"));
  }

  @Test
  void testOptionalBranchPattern_FalseCondition() {
    // Given: Process with optional step when condition is false
    ProcessGraph graph =
        process()
            .startWith(Step1Command.class)
            .withCompensation(CompensateStep1Command.class)
            .thenIf(data -> (Boolean) data.getOrDefault("flag", false))
            .whenTrue(OptionalStepCommand.class)
            .withCompensation(CompensateOptionalCommand.class)
            .then(ContinuationCommand.class)
            .then(Step4Command.class)
            .end();

    // When: Condition is false
    Map<String, Object> dataFalse = Map.of("flag", false);

    // Then: Should skip optional step and go directly to continuation
    assertEquals(Optional.of("Continuation"), graph.getNextStep("Step1", dataFalse));
    assertEquals(Optional.of("Step4"), graph.getNextStep("Continuation", dataFalse));
  }

  @Test
  void testFullConditionalPattern_TrueBranch() {
    // Given: Process with full if-else conditional
    // Step1 -> if(flag) { TrueBranch } else { FalseBranch } -> Step4
    // Note: Start with whenFalse to use BranchBuilder pattern
    ProcessGraph graph =
        process()
            .startWith(Step1Command.class)
            .thenIf(data -> (Boolean) data.getOrDefault("flag", false))
            .whenFalse(FalseBranchCommand.class)
            .withCompensation(CompensateFalseCommand.class)
            .then(Step4Command.class)
            .whenTrue(TrueBranchCommand.class)
            .withCompensation(CompensateTrueCommand.class)
            .then(Step4Command.class)
            .end();

    // When: Condition is true
    Map<String, Object> dataTrue = Map.of("flag", true);

    // Then: Should follow true branch
    assertEquals(Optional.of("TrueBranch"), graph.getNextStep("Step1", dataTrue));
    assertEquals(Optional.of("Step4"), graph.getNextStep("TrueBranch", dataTrue));

    // And: True branch has compensation
    assertTrue(graph.requiresCompensation("TrueBranch"));
    assertEquals(Optional.of("CompensateTrue"), graph.getCompensationStep("TrueBranch"));
  }

  @Test
  void testFullConditionalPattern_FalseBranch() {
    // Given: Process with full if-else conditional
    ProcessGraph graph =
        process()
            .startWith(Step1Command.class)
            .thenIf(data -> (Boolean) data.getOrDefault("flag", false))
            .whenFalse(FalseBranchCommand.class)
            .withCompensation(CompensateFalseCommand.class)
            .then(Step4Command.class)
            .whenTrue(TrueBranchCommand.class)
            .withCompensation(CompensateTrueCommand.class)
            .then(Step4Command.class)
            .end();

    // When: Condition is false
    Map<String, Object> dataFalse = Map.of("flag", false);

    // Then: Should follow false branch
    assertEquals(Optional.of("FalseBranch"), graph.getNextStep("Step1", dataFalse));
    assertEquals(Optional.of("Step4"), graph.getNextStep("FalseBranch", dataFalse));

    // And: False branch has compensation
    assertTrue(graph.requiresCompensation("FalseBranch"));
    assertEquals(Optional.of("CompensateFalse"), graph.getCompensationStep("FalseBranch"));
  }

  @Test
  void testMultipleSequentialConditionals() {
    // Given: Process with multiple conditionals in sequence
    // Step1 -> if(flag1) { Optional1 } -> Step2 -> if(flag2) { Optional2 } -> Step3
    ProcessGraph graph =
        process()
            .startWith(Step1Command.class)
            .thenIf(data -> (Boolean) data.getOrDefault("flag1", false))
            .whenTrue(OptionalStepCommand.class)
            .then(Step2Command.class)
            .thenIf(data -> (Boolean) data.getOrDefault("flag2", false))
            .whenTrue(TrueBranchCommand.class)
            .then(Step3Command.class)
            .end();

    // When: Both flags are true
    Map<String, Object> bothTrue = Map.of("flag1", true, "flag2", true);
    assertEquals(Optional.of("OptionalStep"), graph.getNextStep("Step1", bothTrue));
    assertEquals(Optional.of("Step2"), graph.getNextStep("OptionalStep", bothTrue));
    assertEquals(Optional.of("TrueBranch"), graph.getNextStep("Step2", bothTrue));
    assertEquals(Optional.of("Step3"), graph.getNextStep("TrueBranch", bothTrue));

    // When: First flag true, second false
    Map<String, Object> firstTrue = Map.of("flag1", true, "flag2", false);
    assertEquals(Optional.of("OptionalStep"), graph.getNextStep("Step1", firstTrue));
    assertEquals(Optional.of("Step2"), graph.getNextStep("OptionalStep", firstTrue));
    assertEquals(Optional.of("Step3"), graph.getNextStep("Step2", firstTrue));

    // When: Both flags false
    Map<String, Object> bothFalse = Map.of("flag1", false, "flag2", false);
    assertEquals(Optional.of("Step2"), graph.getNextStep("Step1", bothFalse));
    assertEquals(Optional.of("Step3"), graph.getNextStep("Step2", bothFalse));
  }

  @Test
  void testCompensationNotRequiredForStepsWithoutIt() {
    // Given: Process where some steps have compensation and some don't
    ProcessGraph graph =
        process()
            .startWith(Step1Command.class)
            .withCompensation(CompensateStep1Command.class)
            .then(Step2Command.class) // No compensation
            .then(Step3Command.class)
            .withCompensation(CompensateStep3Command.class)
            .end();

    // Then: Only steps with compensation return true
    assertTrue(graph.requiresCompensation("Step1"));
    assertFalse(graph.requiresCompensation("Step2"));
    assertTrue(graph.requiresCompensation("Step3"));

    // And: Steps without compensation return empty
    assertEquals(Optional.of("CompensateStep1"), graph.getCompensationStep("Step1"));
    assertEquals(Optional.empty(), graph.getCompensationStep("Step2"));
    assertEquals(Optional.of("CompensateStep3"), graph.getCompensationStep("Step3"));
  }

  @Test
  void testTerminalStep_ReturnsEmpty() {
    // Given: A process with terminal step
    ProcessGraph graph = process().startWith(Step1Command.class).then(Step2Command.class).end();

    // When: Getting next step from terminal step
    Map<String, Object> data = new HashMap<>();
    Optional<String> nextStep = graph.getNextStep("Step2", data);

    // Then: Should return empty
    assertTrue(nextStep.isEmpty());
  }

  @Test
  void testUnknownStep_ReturnsEmpty() {
    // Given: A process
    ProcessGraph graph = process().startWith(Step1Command.class).then(Step2Command.class).end();

    // When: Getting next step from unknown step
    Map<String, Object> data = new HashMap<>();
    Optional<String> nextStep = graph.getNextStep("UnknownStep", data);

    // Then: Should return empty
    assertTrue(nextStep.isEmpty());
  }

  @Test
  void testCompensationForUnknownStep_ReturnsFalseAndEmpty() {
    // Given: A process
    ProcessGraph graph =
        process()
            .startWith(Step1Command.class)
            .withCompensation(CompensateStep1Command.class)
            .end();

    // When: Checking compensation for unknown step
    boolean requiresCompensation = graph.requiresCompensation("UnknownStep");
    Optional<String> compensationStep = graph.getCompensationStep("UnknownStep");

    // Then: Should return false and empty
    assertFalse(requiresCompensation);
    assertTrue(compensationStep.isEmpty());
  }

  @Test
  void testRealWorldPaymentFlow_WithFx() {
    // Given: Real payment process with FX
    ProcessGraph graph =
        process()
            .startWith(Step1Command.class) // BookLimits
            .withCompensation(CompensateStep1Command.class) // ReverseLimits
            .thenIf(data -> (Boolean) data.getOrDefault("requiresFx", false))
            .whenTrue(Step2Command.class) // BookFx
            .withCompensation(CompensateStep2Command.class) // UnwindFx
            .then(Step3Command.class) // CreateTransaction
            .withCompensation(CompensateStep3Command.class) // ReverseTransaction
            .then(Step4Command.class) // CreatePayment
            .end();

    // When: FX is required
    Map<String, Object> withFx = Map.of("requiresFx", true);

    // Then: Should follow FX path
    assertEquals("Step1", graph.getInitialStep());
    assertEquals(Optional.of("Step2"), graph.getNextStep("Step1", withFx));
    assertEquals(Optional.of("Step3"), graph.getNextStep("Step2", withFx));
    assertEquals(Optional.of("Step4"), graph.getNextStep("Step3", withFx));
    assertEquals(Optional.empty(), graph.getNextStep("Step4", withFx));

    // And: All steps have compensation except terminal
    assertTrue(graph.requiresCompensation("Step1"));
    assertTrue(graph.requiresCompensation("Step2"));
    assertTrue(graph.requiresCompensation("Step3"));
    assertFalse(graph.requiresCompensation("Step4"));
  }

  @Test
  void testRealWorldPaymentFlow_WithoutFx() {
    // Given: Real payment process without FX
    ProcessGraph graph =
        process()
            .startWith(Step1Command.class) // BookLimits
            .withCompensation(CompensateStep1Command.class) // ReverseLimits
            .thenIf(data -> (Boolean) data.getOrDefault("requiresFx", false))
            .whenTrue(Step2Command.class) // BookFx
            .withCompensation(CompensateStep2Command.class) // UnwindFx
            .then(Step3Command.class) // CreateTransaction
            .withCompensation(CompensateStep3Command.class) // ReverseTransaction
            .then(Step4Command.class) // CreatePayment
            .end();

    // When: FX is not required
    Map<String, Object> withoutFx = Map.of("requiresFx", false);

    // Then: Should skip FX step
    assertEquals("Step1", graph.getInitialStep());
    assertEquals(Optional.of("Step3"), graph.getNextStep("Step1", withoutFx));
    assertEquals(Optional.of("Step4"), graph.getNextStep("Step3", withoutFx));
    assertEquals(Optional.empty(), graph.getNextStep("Step4", withoutFx));

    // And: Compensation path is correct (no Step2 in execution)
    assertTrue(graph.requiresCompensation("Step1"));
    assertTrue(graph.requiresCompensation("Step3"));
    assertFalse(graph.requiresCompensation("Step4"));
  }

  @Test
  void testStepNameDerivation() {
    // Given: Commands with "Command" suffix
    ProcessGraph graph = process().startWith(Step1Command.class).then(Step2Command.class).end();

    // Then: Step names should not have "Command" suffix
    assertEquals("Step1", graph.getInitialStep());
    assertEquals(Optional.of("Step2"), graph.getNextStep("Step1", new HashMap<>()));
  }

  @Test
  void testSimpleParallelExecution() {
    // Given: Process with parallel execution
    // Step1 -> [Parallel1, Parallel2, Parallel3] -> Step4 (join)
    ProcessGraph graph =
        process()
            .startWith(Step1Command.class)
            .thenParallel()
            .branch(ParallelStep1Command.class)
            .branch(ParallelStep2Command.class)
            .branch(ParallelStep3Command.class)
            .joinAt(Step4Command.class)
            .end();

    // Then: Initial step is correct
    assertEquals("Step1", graph.getInitialStep());

    // And: Step1 is a parallel step
    assertTrue(graph.isParallelStep("Step1"));

    // And: Parallel branches are correctly defined
    List<String> branches = graph.getParallelBranches("Step1");
    assertEquals(3, branches.size());
    assertTrue(branches.contains("ParallelStep1"));
    assertTrue(branches.contains("ParallelStep2"));
    assertTrue(branches.contains("ParallelStep3"));

    // And: Join step is correct
    assertEquals(Optional.of("Step4"), graph.getJoinStep("Step1"));

    // And: Each parallel branch goes to the join step
    Map<String, Object> data = new HashMap<>();
    assertEquals(Optional.of("Step4"), graph.getNextStep("ParallelStep1", data));
    assertEquals(Optional.of("Step4"), graph.getNextStep("ParallelStep2", data));
    assertEquals(Optional.of("Step4"), graph.getNextStep("ParallelStep3", data));

    // And: Step4 is terminal
    assertEquals(Optional.empty(), graph.getNextStep("Step4", data));
  }

  @Test
  void testParallelExecutionWithCompensation() {
    // Given: Parallel execution where branches have compensation
    ProcessGraph graph =
        process()
            .startWith(Step1Command.class)
            .withCompensation(CompensateStep1Command.class)
            .thenParallel()
            .branch(ParallelStep1Command.class)
            .withCompensation(CompensateStep1Command.class)
            .branch(ParallelStep2Command.class)
            .withCompensation(CompensateStep2Command.class)
            .branch(ParallelStep3Command.class)
            .withCompensation(CompensateStep3Command.class)
            .joinAt(Step4Command.class)
            .end();

    // Then: Source step has compensation
    assertTrue(graph.requiresCompensation("Step1"));
    assertEquals(Optional.of("CompensateStep1"), graph.getCompensationStep("Step1"));

    // And: All parallel branches have compensation
    assertTrue(graph.requiresCompensation("ParallelStep1"));
    assertTrue(graph.requiresCompensation("ParallelStep2"));
    assertTrue(graph.requiresCompensation("ParallelStep3"));

    assertEquals(Optional.of("CompensateStep1"), graph.getCompensationStep("ParallelStep1"));
    assertEquals(Optional.of("CompensateStep2"), graph.getCompensationStep("ParallelStep2"));
    assertEquals(Optional.of("CompensateStep3"), graph.getCompensationStep("ParallelStep3"));
  }

  @Test
  void testParallelExecutionWithContinuation() {
    // Given: Parallel execution followed by more steps
    // Step1 -> [Parallel1, Parallel2] -> Step2 -> Step3
    ProcessGraph graph =
        process()
            .startWith(Step1Command.class)
            .thenParallel()
            .branch(ParallelStep1Command.class)
            .branch(ParallelStep2Command.class)
            .joinAt(Step2Command.class)
            .then(Step3Command.class)
            .end();

    // Then: Parallel execution is correct
    assertTrue(graph.isParallelStep("Step1"));
    assertEquals(2, graph.getParallelBranches("Step1").size());

    // And: Join step continues to next step
    Map<String, Object> data = new HashMap<>();
    assertEquals(Optional.of("Step3"), graph.getNextStep("Step2", data));
    assertEquals(Optional.empty(), graph.getNextStep("Step3", data));
  }

  @Test
  void testSequentialThenParallelExecution() {
    // Given: Sequential steps followed by parallel execution
    // Step1 -> Step2 -> [Parallel1, Parallel2, Parallel3] -> Step3
    ProcessGraph graph =
        process()
            .startWith(Step1Command.class)
            .then(Step2Command.class)
            .thenParallel()
            .branch(ParallelStep1Command.class)
            .branch(ParallelStep2Command.class)
            .branch(ParallelStep3Command.class)
            .joinAt(Step3Command.class)
            .end();

    // Then: Sequential navigation works
    Map<String, Object> data = new HashMap<>();
    assertEquals(Optional.of("Step2"), graph.getNextStep("Step1", data));

    // And: Step2 spawns parallel execution
    assertTrue(graph.isParallelStep("Step2"));
    assertEquals(3, graph.getParallelBranches("Step2").size());

    // And: All branches join at Step3
    assertEquals(Optional.of("Step3"), graph.getJoinStep("Step2"));
  }

  @Test
  void testConditionalWithParallelBranch() {
    // Given: Conditional where true branch leads to parallel execution
    // Step1 -> if(parallel) { TrueBranch -> [Parallel1, Parallel2] join Step3 } else { Step3 }
    ProcessGraph graph =
        process()
            .startWith(Step1Command.class)
            .thenIf(data -> (Boolean) data.getOrDefault("parallel", false))
            .whenTrue(TrueBranchCommand.class)
            .then(Step2Command.class)
            .thenParallel()
            .branch(ParallelStep1Command.class)
            .branch(ParallelStep2Command.class)
            .joinAt(Step3Command.class)
            .end();

    // When: Condition is true
    Map<String, Object> dataTrue = Map.of("parallel", true);

    // Then: Should follow true branch
    assertEquals(Optional.of("TrueBranch"), graph.getNextStep("Step1", dataTrue));
    assertEquals(Optional.of("Step2"), graph.getNextStep("TrueBranch", dataTrue));

    // And: Step2 spawns parallel execution
    assertTrue(graph.isParallelStep("Step2"));
    assertEquals(2, graph.getParallelBranches("Step2").size());

    // When: Condition is false
    Map<String, Object> dataFalse = Map.of("parallel", false);

    // Then: Should skip to Step2 directly (which spawns parallel)
    assertEquals(Optional.of("Step2"), graph.getNextStep("Step1", dataFalse));
    assertTrue(graph.isParallelStep("Step2"));
  }

  @Test
  void testParallelWithMixedCompensation() {
    // Given: Parallel execution where some branches have compensation and some don't
    ProcessGraph graph =
        process()
            .startWith(Step1Command.class)
            .thenParallel()
            .branch(ParallelStep1Command.class)
            .withCompensation(CompensateStep1Command.class)
            .branch(ParallelStep2Command.class)
            // No compensation
            .branch(ParallelStep3Command.class)
            .withCompensation(CompensateStep3Command.class)
            .joinAt(Step2Command.class)
            .end();

    // Then: Only branches with compensation return true
    assertTrue(graph.requiresCompensation("ParallelStep1"));
    assertFalse(graph.requiresCompensation("ParallelStep2"));
    assertTrue(graph.requiresCompensation("ParallelStep3"));
  }

  @Test
  void testNonParallelSteps() {
    // Given: A process with no parallel execution
    ProcessGraph graph =
        process()
            .startWith(Step1Command.class)
            .then(Step2Command.class)
            .then(Step3Command.class)
            .end();

    // Then: No steps should be parallel
    assertFalse(graph.isParallelStep("Step1"));
    assertFalse(graph.isParallelStep("Step2"));
    assertFalse(graph.isParallelStep("Step3"));

    // And: Parallel branches should be empty
    assertEquals(0, graph.getParallelBranches("Step1").size());
    assertEquals(0, graph.getParallelBranches("Step2").size());

    // And: Join step should be empty
    assertTrue(graph.getJoinStep("Step1").isEmpty());
  }
}
