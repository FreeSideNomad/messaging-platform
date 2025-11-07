package com.acme.payments.orchestration;

import static com.acme.reliable.process.ProcessGraphBuilder.process;

import com.acme.payments.application.command.*;
import com.acme.reliable.command.DomainCommand;
import com.acme.reliable.core.Jsons;
import com.acme.reliable.process.ProcessConfiguration;
import com.acme.reliable.process.ProcessGraph;
import jakarta.inject.Singleton;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Payment process definition using declarative DAG builder.
 *
 * <p>Process flow: 1. BookLimits (with compensation: ReverseLimits) 2. If requiresFx: -> BookFx
 * (with compensation: UnwindFx) Else: -> CreateTransaction (with compensation: ReverseTransaction)
 * 3. CreateTransaction (with compensation: ReverseTransaction) 4. CreatePayment (terminal)
 *
 * <p>Also acts as a handler for InitiateSimplePaymentCommand to start the process.
 */
@Singleton
@Slf4j
public class SimplePaymentProcessDefinition implements ProcessConfiguration {

  @Override
  public String getProcessType() {
    return "SimplePayment";
  }

  @Override
  public Class<? extends DomainCommand> getInitiationCommandType() {
    return InitiateSimplePaymentCommand.class;
  }

  /**
   * Build initial state from InitiateSimplePaymentCommand. Called before the process starts.
   *
   * @param cmd the initiation command
   * @return initial process state as a map
   */
  @Override
  public Map<String, Object> initializeProcessState(DomainCommand command) {
    return initializeProcessState((InitiateSimplePaymentCommand) command);
  }

  public Map<String, Object> initializeProcessState(InitiateSimplePaymentCommand cmd) {
    log.info(
        "Initializing SimplePayment process for customer {} with debit amount {}",
        cmd.customerId(),
        cmd.debitAmount().amount());

    // Serialize the entire command to a map
    Map<String, Object> processState = Jsons.toMap(cmd);

    // Add any derived state
    processState.put("requiresFx", cmd.requiresFx());

    log.info("Process state initialized with requiresFx={}", cmd.requiresFx());

    return processState;
  }

  @Override
  public ProcessGraph defineProcess() {
    return process()
        .startWith(BookLimitsCommand.class)
        .withCompensation(ReverseLimitsCommand.class)
        .thenIf(data -> (Boolean) data.get("requiresFx"))
        .whenTrue(BookFxCommand.class)
        .withCompensation(UnwindFxCommand.class)
        .then(CreateTransactionCommand.class)
        .withCompensation(ReverseTransactionCommand.class)
        .then(CreatePaymentCommand.class)
        .end();
  }

  @Override
  public boolean isRetryable(String step, String error) {
    // Retry on transient errors
    if (error == null) {
      return false;
    }
    String lowerError = error.toLowerCase(java.util.Locale.ROOT);
    return lowerError.contains("timeout")
        || lowerError.contains("connection")
        || lowerError.contains("temporary");
  }

  @Override
  public int getMaxRetries(String step) {
    return 3;
  }
}
