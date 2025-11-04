package com.acme.payments.orchestration;

import com.acme.payments.application.command.*;
import com.acme.reliable.core.Jsons;
import com.acme.reliable.process.ProcessConfiguration;
import com.acme.reliable.process.ProcessGraph;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import static com.acme.reliable.process.ProcessGraphBuilder.process;

/**
 * Payment process definition using declarative DAG builder.
 *
 * Process flow:
 * 1. BookLimits (with compensation: ReverseLimits)
 * 2. If requiresFx:
 *      -> BookFx (with compensation: UnwindFx)
 *    Else:
 *      -> CreateTransaction (with compensation: ReverseTransaction)
 * 3. CreateTransaction (with compensation: ReverseTransaction)
 * 4. CreatePayment (terminal)
 *
 * Also acts as a handler for InitiateSimplePaymentCommand to start the process.
 */
@Singleton
@Slf4j
public class SimplePaymentProcessDefinition implements ProcessConfiguration {

    @Override
    public String getProcessType() {
        return "SimplePayment";
    }

    /**
     * Command handler for InitiateSimplePaymentCommand.
     * This method will be auto-discovered by AutoCommandHandlerRegistry
     * and builds the initial process state from the command.
     *
     * @param cmd the initiation command
     * @return initial process state as a map
     */
    public Map<String, Object> handleInitiateSimplePayment(InitiateSimplePaymentCommand cmd) {
        log.info("Initializing SimplePayment process for customer {} with debit amount {}",
            cmd.customerId(), cmd.debitAmount().amount());

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
        String lowerError = error.toLowerCase();
        return lowerError.contains("timeout") ||
            lowerError.contains("connection") ||
            lowerError.contains("temporary");
    }

    @Override
    public int getMaxRetries(String step) {
        return 3;
    }
}
