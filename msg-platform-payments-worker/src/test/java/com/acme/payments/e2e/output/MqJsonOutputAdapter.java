package com.acme.payments.e2e.output;

import com.acme.payments.application.command.InitiateCreateAccountProcess;
import com.acme.payments.application.command.CreateTransactionCommand;
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
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter for generating MQ message JSON files.
 * Output is newline-delimited JSON (JSONL) format.
 */
@Slf4j
public class MqJsonOutputAdapter {

    private static final String REPLY_QUEUE = "REPLY.QUEUE";
    private final ObjectMapper objectMapper;

    public MqJsonOutputAdapter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Write all scenario messages to separate JSONL files
     */
    public void writeAllMessages(E2ETestScenario scenario, String outputDir) throws IOException {
        Path mqDir = Paths.get(outputDir, "mq");
        Files.createDirectories(mqDir);

        log.info("Writing MQ message files to {}", mqDir);

        // Write accounts
        Path accountsFile = mqDir.resolve("accounts.jsonl");
        writeAccountMessages(scenario.getAccountCommands(), accountsFile);
        log.info("Wrote {} account messages to {}", scenario.getAccountCommands().size(), accountsFile);

        // Write opening credits
        Path creditsFile = mqDir.resolve("opening-credits.jsonl");
        writeTransactionMessages(scenario.getOpeningTransactions(), creditsFile);
        log.info("Wrote {} opening credit messages to {}", scenario.getOpeningTransactions().size(), creditsFile);

        // Write funding transactions
        Path fundingFile = mqDir.resolve("funding-txns.jsonl");
        writeTransactionMessages(scenario.getFundingTransactions(), fundingFile);
        log.info("Wrote {} funding transaction messages to {}", scenario.getFundingTransactions().size(), fundingFile);

        // Write payments
        Path paymentsFile = mqDir.resolve("payments.jsonl");
        writePaymentMessages(scenario.getPaymentCommands(), paymentsFile);
        log.info("Wrote {} payment messages to {}", scenario.getPaymentCommands().size(), paymentsFile);

        // Write combined sequenced file
        Path allFile = mqDir.resolve("all-messages.jsonl");
        writeSequencedMessages(scenario, allFile);
        log.info("Wrote all {} messages to {}", scenario.getMetrics().totalCommands(), allFile);

        log.info("MQ message generation complete");
    }

    /**
     * Write account creation messages
     */
    public void writeAccountMessages(List<InitiateCreateAccountProcess> commands, Path outputFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputFile))) {
            for (InitiateCreateAccountProcess command : commands) {
                String message = wrapCommand("InitiateCreateAccountProcess", command);
                writer.println(message);
            }
        }
    }

    /**
     * Write transaction creation messages
     */
    public void writeTransactionMessages(List<CreateTransactionCommand> commands, Path outputFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputFile))) {
            for (CreateTransactionCommand command : commands) {
                String message = wrapCommand("CreateTransaction", command);
                writer.println(message);
            }
        }
    }

    /**
     * Write payment initiation messages
     */
    public void writePaymentMessages(List<InitiateSimplePaymentCommand> commands, Path outputFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputFile))) {
            for (InitiateSimplePaymentCommand command : commands) {
                String message = wrapCommand("InitiateSimplePayment", command);
                writer.println(message);
            }
        }
    }

    /**
     * Write all messages in correct sequence
     */
    private void writeSequencedMessages(E2ETestScenario scenario, Path outputFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputFile))) {
            // Accounts first
            for (InitiateCreateAccountProcess command : scenario.getAccountCommands()) {
                writer.println(wrapCommand("InitiateCreateAccountProcess", command));
            }

            // Opening credits
            for (CreateTransactionCommand command : scenario.getOpeningTransactions()) {
                writer.println(wrapCommand("CreateTransaction", command));
            }

            // Funding transactions
            for (CreateTransactionCommand command : scenario.getFundingTransactions()) {
                writer.println(wrapCommand("CreateTransaction", command));
            }

            // Payments
            for (InitiateSimplePaymentCommand command : scenario.getPaymentCommands()) {
                writer.println(wrapCommand("InitiateSimplePayment", command));
            }
        }
    }

    /**
     * Wrap command in MQ message envelope
     */
    private String wrapCommand(String commandType, Object payload) throws IOException {
        Map<String, Object> message = new HashMap<>();
        message.put("messageId", UUID.randomUUID().toString());
        message.put("correlationId", UUID.randomUUID().toString());
        message.put("timestamp", Instant.now().toString());
        message.put("commandType", commandType);
        message.put("payload", payload);
        message.put("replyTo", REPLY_QUEUE);

        return objectMapper.writeValueAsString(message);
    }
}
