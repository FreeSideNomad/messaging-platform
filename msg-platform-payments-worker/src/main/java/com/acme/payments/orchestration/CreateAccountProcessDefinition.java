package com.acme.payments.orchestration;

import com.acme.payments.application.command.CompleteAccountCreationCommand;
import com.acme.payments.application.command.CreateAccountCommand;
import com.acme.payments.application.command.CreateLimitsCommand;
import com.acme.reliable.core.Jsons;
import com.acme.reliable.process.ProcessConfiguration;
import com.acme.reliable.process.ProcessGraph;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import static com.acme.reliable.process.ProcessGraphBuilder.process;

/**
 * Account creation process definition with optional limit creation.
 *
 * Process flow:
 * 1. CreateAccount (creates the account aggregate)
 * 2. CreateLimits (conditionally creates limits if limitBased=true)
 *
 * Both steps are idempotent and can be safely retried.
 * No compensation needed since these are creation operations.
 *
 * Also acts as a handler for CreateAccountCommand to start the process.
 */
@Singleton
@Slf4j
public class CreateAccountProcessDefinition implements ProcessConfiguration {

    @Override
    public String getProcessType() {
        return "CreateAccount";
    }

    /**
     * Command handler for CreateAccountCommand.
     * This method will be auto-discovered by AutoCommandHandlerRegistry
     * and builds the initial process state from the command.
     *
     * @param cmd the account creation command
     * @return initial process state as a map
     */
    public Map<String, Object> handleCreateAccount(CreateAccountCommand cmd) {
        log.info("Initializing CreateAccount process for customer {} with currency {} limitBased={}",
            cmd.customerId(), cmd.currencyCode(), cmd.limitBased());

        // Serialize the entire command to a map for process state
        Map<String, Object> processState = Jsons.toMap(cmd);

        log.info("CreateAccount process state initialized for customer {} with account type {}",
            cmd.customerId(), cmd.accountType());

        return processState;
    }

    @Override
    public ProcessGraph defineProcess() {
        // Conditional flow: CreateAccount -> (if limitBased) CreateLimits -> Complete
        // If limitBased=false, skip CreateLimits and go directly to Complete
        return process()
            .startWith(CreateAccountCommand.class)
            .thenIf(data -> {
                // Check if limitBased is true and limits exist
                Object limitBased = data.get("limitBased");
                Object limits = data.get("limits");
                return Boolean.TRUE.equals(limitBased) && limits != null;
            })
            .whenTrue(CreateLimitsCommand.class)
                .then(CompleteAccountCreationCommand.class)
            .then(CompleteAccountCreationCommand.class) // Continue after optional branch
            .end();
    }

    @Override
    public boolean isRetryable(String step, String error) {
        // Retry on transient errors (database connection issues, etc.)
        // Account creation is idempotent, so retrying is safe
        return error != null && (
            error.contains("timeout") ||
            error.contains("connection") ||
            error.contains("temporary") ||
            error.contains("deadlock")
        );
    }

    @Override
    public int getMaxRetries(String step) {
        // Allow up to 3 retries for transient failures
        return 3;
    }
}
