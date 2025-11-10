package com.acme.payments.e2e.output;

import com.acme.payments.application.command.CreateTransactionCommand;
import com.acme.payments.application.command.InitiateCreateAccountProcess;
import com.acme.payments.application.command.InitiateSimplePaymentCommand;
import com.acme.payments.e2e.scenario.E2ETestScenario;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Adapter for generating Vegeta HTTP load test target files. Uses inline JSON format (no external
 * file references).
 */
@Slf4j
public class VegetaOutputAdapter {

    private static final String BASE_URL = "http://localhost:8080";
    private final ObjectMapper objectMapper;

    public VegetaOutputAdapter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Write all scenario targets to separate files
     */
    public void writeSequencedTargets(E2ETestScenario scenario, String outputDir) throws IOException {
        Path vegetaDir = Paths.get(outputDir, "vegeta");
        Files.createDirectories(vegetaDir);

        log.info("Writing Vegeta target files to {}", vegetaDir);

        // Write accounts
        Path accountsFile = vegetaDir.resolve("01-accounts.txt");
        writeAccountTargets(scenario.getAccountCommands(), accountsFile);
        log.info("Wrote {} account targets to {}", scenario.getAccountCommands().size(), accountsFile);

        // Write opening credits
        Path creditsFile = vegetaDir.resolve("02-opening-credits.txt");
        writeTransactionTargets(scenario.getOpeningTransactions(), creditsFile);
        log.info(
                "Wrote {} opening credit targets to {}",
                scenario.getOpeningTransactions().size(),
                creditsFile);

        // Write funding transactions
        Path fundingFile = vegetaDir.resolve("03-funding-txns.txt");
        writeTransactionTargets(scenario.getFundingTransactions(), fundingFile);
        log.info(
                "Wrote {} funding transaction targets to {}",
                scenario.getFundingTransactions().size(),
                fundingFile);

        // Write payments
        Path paymentsFile = vegetaDir.resolve("04-payments.txt");
        writePaymentTargets(scenario.getPaymentCommands(), paymentsFile);
        log.info("Wrote {} payment targets to {}", scenario.getPaymentCommands().size(), paymentsFile);

        log.info(
                "Vegeta target generation complete. Total commands: {}",
                scenario.getMetrics().totalCommands());
    }

    /**
     * Write account creation process targets
     */
    public void writeAccountTargets(List<InitiateCreateAccountProcess> commands, Path outputFile)
            throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputFile))) {
            for (InitiateCreateAccountProcess command : commands) {
                writeHttpTarget(writer, "POST", "/api/accounts", command);
            }
        }
    }

    /**
     * Write transaction creation targets
     */
    public void writeTransactionTargets(List<CreateTransactionCommand> commands, Path outputFile)
            throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputFile))) {
            for (CreateTransactionCommand command : commands) {
                writeHttpTarget(writer, "POST", "/api/transactions", command);
            }
        }
    }

    /**
     * Write payment initiation targets
     */
    public void writePaymentTargets(List<InitiateSimplePaymentCommand> commands, Path outputFile)
            throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputFile))) {
            for (InitiateSimplePaymentCommand command : commands) {
                writeHttpTarget(writer, "POST", "/api/payments", command);
            }
        }
    }

    /**
     * Write a single HTTP target with inline JSON
     */
    private void writeHttpTarget(PrintWriter writer, String method, String path, Object body)
            throws IOException {
        String url = BASE_URL + path;
        String json = commandToJson(body);

        // Vegeta format: METHOD URL\nHeader\n\nBody\n\n
        writer.println(method + " " + url);
        writer.println("Content-Type: application/json");
        writer.println();
        writer.println(json);
        writer.println();
    }

    /**
     * Serialize command to compact inline JSON (single line, no pretty print)
     */
    private String commandToJson(Object command) throws IOException {
        return objectMapper.writeValueAsString(command);
    }
}
