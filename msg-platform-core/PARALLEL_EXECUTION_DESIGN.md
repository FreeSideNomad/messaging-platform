# Parallel Execution Design for Process Graphs

## Overview

This document describes the design for adding parallel execution support to the ProcessGraphBuilder.

## Patterns to Support

### 1. Simple Parallel Split with Join

Execute multiple steps in parallel, then wait for all to complete before continuing.

```java
process()
    .startWith(Step1Command.class)
    .thenParallel()
        .branch(ParallelStep1Command.class)
        .branch(ParallelStep2Command.class)
        .branch(ParallelStep3Command.class)
        .joinAt(ContinuationCommand.class)
    .then(FinalStepCommand.class)
    .end();
```

**Flow**: Step1 → [ParallelStep1, ParallelStep2, ParallelStep3] → Continuation → FinalStep

### 2. Parallel Branches with Compensation

Each parallel branch can have its own compensation.

```java
process()
    .startWith(Step1Command.class)
    .thenParallel()
        .branch(ParallelStep1Command.class)
            .withCompensation(CompensateParallel1Command.class)
        .branch(ParallelStep2Command.class)
            .withCompensation(CompensateParallel2Command.class)
        .joinAt(ContinuationCommand.class)
    .end();
```

### 3. Conditional Parallel Execution

Parallel execution can be conditional.

```java
process()
    .startWith(Step1Command.class)
    .thenIf(data -> (Boolean) data.get("runParallel"))
        .whenTrue(ParallelGatewayCommand.class)
            .thenParallel()
                .branch(ParallelStep1Command.class)
                .branch(ParallelStep2Command.class)
                .joinAt(ContinuationCommand.class)
    .then(FinalStepCommand.class)
    .end();
```

## Implementation Approach

### Option 1: Parallel as Special Step Type (Recommended)

Treat parallel execution as a special step that spawns multiple commands and tracks their completion.

**Pros**:

- Simpler model - parallel execution is encapsulated
- Easier to implement compensation (compensate all completed parallel branches)
- Natural execution model - orchestrator waits for all parallel steps before proceeding

**Cons**:

- Requires execution engine changes to understand parallel steps
- Less visible in the graph structure

### Option 2: Parallel as Graph Structure

Model parallel execution explicitly in the graph with explicit fork/join nodes.

**Pros**:

- More explicit graph structure
- Could support more complex parallel patterns (barriers, partial joins, etc.)

**Cons**:

- More complex graph navigation logic
- Harder to implement and test
- Overkill for most use cases

## Recommended Implementation (Option 1)

### New Classes

#### 1. ParallelStep

```java
public class ParallelStep extends ProcessStep {
    private final List<String> parallelBranches;
    private final String joinStep;

    // Methods to track which branches have completed
    // Methods to determine if all branches are done
}
```

#### 2. ParallelBranchBuilder

```java
public class ParallelBranchBuilder {
    private final List<ParallelBranch> branches;

    public ParallelBranchBuilder branch(Class<? extends DomainCommand> commandClass);
    public ParallelBranchConfig withCompensation(Class<? extends DomainCommand> compensationClass);
    public StepBuilder joinAt(Class<? extends DomainCommand> joinStepClass);
}
```

### Execution Model

1. When a parallel step is reached:
    - Orchestrator spawns N command messages (one for each branch)
    - Tracks completion of each branch in process state
    - Waits until all branches complete before proceeding to join step

2. Failure Handling:
    - If any branch fails, the entire parallel section fails
    - Compensation runs for all successfully completed branches
    - Compensation can run in parallel or sequential (configurable)

3. Process State:
    - Store parallel execution state: `parallelExecution: { step1: completed, step2: pending, step3: completed }`
    - Track which branch failures occurred for error reporting

## Alternative: Task-Based Parallel Execution

Instead of building parallel into the process graph, we could use a different pattern:

```java
process()
    .startWith(Step1Command.class)
    .then(ScheduleParallelTasksCommand.class)  // Creates parallel tasks
    .then(WaitForTasksCommand.class)            // Waits for completion
    .then(ContinuationCommand.class)
    .end();
```

This pushes parallel execution complexity into specific command handlers rather than the process orchestration
framework.

**Pros**:

- Simpler framework - no changes to process graph
- Maximum flexibility - handlers control parallel execution
- Clear separation of concerns

**Cons**:

- Less declarative
- Each use case needs custom implementation
- Harder to visualize and monitor

## Decision

For MVP: **Implement Option 1 (Parallel as Special Step Type)**

This provides a good balance of:

- Declarative syntax
- Clear semantics
- Reasonable implementation complexity
- Sufficient flexibility for most use cases

## Open Questions

1. Should compensation of parallel branches run sequentially or in parallel?
    - **Recommendation**: Sequential, in reverse order of execution (safer)

2. How to handle partial failures?
    - **Recommendation**: Fail fast - if any branch fails, fail the entire parallel section

3. Should we support barriers (wait for subset of branches)?
    - **Recommendation**: Not in MVP, can add later if needed

4. Maximum number of parallel branches?
    - **Recommendation**: No hard limit, but warn if > 10 branches

5. Timeout handling for slow parallel branches?
    - **Recommendation**: Use existing retry/timeout logic, no special handling in MVP
