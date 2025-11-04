package com.acme.payments.orchestration;

import com.acme.payments.application.command.CreateAccountCommand;
import com.acme.reliable.core.Jsons;
import com.acme.reliable.process.ProcessConfiguration;
import com.acme.reliable.process.ProcessGraph;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import static com.acme.reliable.process.ProcessGraphBuilder.process;

/**
 * Account creation process definition.
 *
 * This is a simple single-step process without compensation since account
 * creation is idempotent (attempting to create an account with the same
 * parameters is safe to retry).
 *
 * Process flow:
 * 1. CreateAccount (terminal, no compensation needed)
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
        log.info("Initializing CreateAccount process for customer {} with currency {}",
            cmd.customerId(), cmd.currencyCode());

        // Serialize the entire command to a map for process state
        Map<String, Object> processState = Jsons.toMap(cmd);

        log.info("CreateAccount process state initialized for customer {} with account type {}",
            cmd.customerId(), cmd.accountType());

        return processState;
    }

    @Override
    public ProcessGraph defineProcess() {
        // Simple linear process with single step
        return process()
            .startWith(CreateAccountCommand.class)
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
