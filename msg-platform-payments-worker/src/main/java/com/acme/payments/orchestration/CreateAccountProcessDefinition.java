package com.acme.payments.orchestration;

import static com.acme.reliable.process.ProcessGraphBuilder.process;

import com.acme.payments.application.command.CompleteAccountCreationCommand;
import com.acme.payments.application.command.CreateAccountCommand;
import com.acme.payments.application.command.CreateLimitsCommand;
import com.acme.payments.application.command.InitiateCreateAccountProcess;
import com.acme.reliable.command.DomainCommand;
import com.acme.reliable.core.Jsons;
import com.acme.reliable.process.ProcessConfiguration;
import com.acme.reliable.process.ProcessGraph;
import jakarta.inject.Singleton;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Account creation process definition with optional limit creation.
 *
 * <p>Process flow: 1. InitiateCreateAccountProcess (starts the process) 2. CreateAccount (creates
 * the account aggregate) 3. CreateLimits (conditionally creates limits if limitBased=true) 4.
 * CompleteAccountCreation (marks process as complete)
 *
 * <p>All steps are idempotent and can be safely retried. No compensation needed since these are
 * creation operations.
 */
@Singleton
@Slf4j
public class CreateAccountProcessDefinition implements ProcessConfiguration {

  @Override
  public String getProcessType() {
    return "InitiateCreateAccountProcess";
  }

  @Override
  public Class<? extends DomainCommand> getInitiationCommandType() {
    return InitiateCreateAccountProcess.class;
  }

  /**
   * Initializes process state from InitiateCreateAccountProcess command. This method builds the
   * initial process state from the command when the process starts. Note: This is NOT a command
   * handler - it's called by the process manager.
   *
   * @param cmd the process initiation command
   * @return initial process state as a map
   */
  @Override
  public Map<String, Object> initializeProcessState(DomainCommand command) {
    return initializeProcessState((InitiateCreateAccountProcess) command);
  }

  public Map<String, Object> initializeProcessState(InitiateCreateAccountProcess cmd) {
    log.info(
        "Initializing CreateAccount process for customer {} with currency {} limitBased={}",
        cmd.customerId(),
        cmd.currencyCode(),
        cmd.limitBased());

    // Serialize the entire command to a map for process state
    Map<String, Object> processState = Jsons.toMap(cmd);

    log.info(
        "CreateAccount process state initialized for customer {} with account type {}",
        cmd.customerId(),
        cmd.accountType());

    return processState;
  }

  @Override
  public ProcessGraph defineProcess() {
    // Conditional flow: CreateAccount -> (if limitBased)
    // CreateLimits -> Complete
    // If limitBased=false, skip CreateLimits and go directly to Complete
    return process()
        .startWith(CreateAccountCommand.class)
        .thenIf(
            data -> {
              // Check if limitBased is true and limits exist
              Object limitBased = data.get("limitBased");
              Object limits = data.get("limits");
              return Boolean.TRUE.equals(limitBased) && limits != null;
            })
        .whenTrue(CreateLimitsCommand.class)
        .then(CompleteAccountCreationCommand.class) // Continue after optional branch
        .end();
  }

  @Override
  public boolean isRetryable(String step, String error) {
    // Retry on transient errors (database connection issues, etc.)
    // Account creation is idempotent, so retrying is safe
    return error != null
        && (error.contains("timeout")
            || error.contains("connection")
            || error.contains("temporary")
            || error.contains("deadlock"));
  }

  @Override
  public int getMaxRetries(String step) {
    // Allow up to 3 retries for transient failures
    return 3;
  }
}
