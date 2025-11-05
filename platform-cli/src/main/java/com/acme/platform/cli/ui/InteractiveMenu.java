package com.acme.platform.cli.ui;

import com.acme.platform.cli.model.PaginatedResult;
import com.acme.platform.cli.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class InteractiveMenu {
    private final Terminal terminal;
    private final LineReader reader;
    private final ObjectMapper objectMapper;
    private boolean running = true;

    public InteractiveMenu() {
        try {
            this.terminal = TerminalBuilder.builder().system(true).build();
            this.reader = LineReaderBuilder.builder().terminal(terminal).build();
            this.objectMapper = new ObjectMapper();
            this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize interactive menu", e);
        }
    }

    public void start() {
        printWelcome();

        while (running) {
            try {
                showMainMenu();
                String choice = reader.readLine("Select option: ");
                handleMainMenuChoice(choice.trim());
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    private void printWelcome() {
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("        Messaging Platform CLI - Interactive Mode");
        System.out.println("=".repeat(60));
        System.out.println();
    }

    private void showMainMenu() {
        System.out.println("\n--- Main Menu ---");
        System.out.println("1. Database Queries");
        System.out.println("2. API Commands");
        System.out.println("3. Message Queue Status");
        System.out.println("4. Kafka Topics");
        System.out.println("5. Docker Management");
        System.out.println("6. Exit");
        System.out.println();
    }

    private void handleMainMenuChoice(String choice) {
        switch (choice) {
            case "1" -> databaseMenu();
            case "2" -> apiMenu();
            case "3" -> mqMenu();
            case "4" -> kafkaMenu();
            case "5" -> dockerMenu();
            case "6" -> {
                running = false;
                System.out.println("Goodbye!");
            }
            default -> System.out.println("Invalid option. Please try again.");
        }
    }

    private void databaseMenu() {
        System.out.println("\n--- Database Queries ---");
        System.out.println("1. List all tables");
        System.out.println("2. Query a table");
        System.out.println("3. Get table info");
        System.out.println("4. Back to main menu");
        System.out.println();

        String choice = reader.readLine("Select option: ");

        try {
            DatabaseService dbService = DatabaseService.getInstance();
            switch (choice.trim()) {
                case "1" -> {
                    List<String> tables = dbService.listTables();
                    System.out.println("\nAvailable Tables:");
                    System.out.println("=".repeat(50));
                    tables.forEach(table -> System.out.println("  - " + table));
                    System.out.println("\nTotal: " + tables.size() + " tables");
                }
                case "2" -> {
                    String tableName = reader.readLine("Enter table name: ");
                    int page = 1;
                    boolean browsing = true;

                    while (browsing) {
                        PaginatedResult result = dbService.queryTable(tableName, page, null);
                        printTableData(result);

                        System.out.println("\nOptions: (n)ext, (p)revious, (b)ack");
                        String nav = reader.readLine("> ").toLowerCase();

                        switch (nav) {
                            case "n" -> {
                                if (page < result.getPagination().getTotalPages()) {
                                    page++;
                                } else {
                                    System.out.println("Already on last page.");
                                }
                            }
                            case "p" -> {
                                if (page > 1) {
                                    page--;
                                } else {
                                    System.out.println("Already on first page.");
                                }
                            }
                            case "b" -> browsing = false;
                            default -> System.out.println("Invalid option.");
                        }
                    }
                }
                case "3" -> {
                    String tableName = reader.readLine("Enter table name: ");
                    Map<String, Object> info = dbService.getTableInfo(tableName);
                    System.out.println("\nTable: " + tableName);
                    System.out.println("=".repeat(50));
                    System.out.println("Row Count: " + info.get("rowCount"));
                    System.out.println("\nColumns:");
                    @SuppressWarnings("unchecked")
                    List<Map<String, String>> columns = (List<Map<String, String>>) info.get("columns");
                    columns.forEach(col ->
                            System.out.printf("  - %s (%s) %s\n",
                                    col.get("name"),
                                    col.get("type"),
                                    "YES".equals(col.get("nullable")) ? "NULL" : "NOT NULL")
                    );
                }
                case "4" -> {
                    // Back to main menu
                }
                default -> System.out.println("Invalid option.");
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private void apiMenu() {
        System.out.println("\n--- API Commands ---");
        System.out.println("1. Browse and execute command samples");
        System.out.println("2. Execute command (manual file path)");
        System.out.println("3. Make GET request");
        System.out.println("4. Back to main menu");
        System.out.println();

        String choice = reader.readLine("Select option: ");

        try {
            ApiService apiService = ApiService.getInstance();

            switch (choice.trim()) {
                case "1" -> browseAndExecuteCommandSamples(apiService);
                case "2" -> {
                    String commandName = reader.readLine("Enter command name: ");
                    String payloadPath = reader.readLine("Enter payload file path: ");
                    String prefix = reader.readLine("Enter idempotency prefix (or press Enter for default): ");

                    File payloadFile = new File(payloadPath);
                    if (!payloadFile.exists()) {
                        System.err.println("Payload file not found: " + payloadPath);
                        return;
                    }

                    ApiService.ApiResponse response = apiService.executeCommand(
                            commandName,
                            payloadFile,
                            prefix.isEmpty() ? null : prefix
                    );

                    printApiResponse(response);
                }
                case "3" -> {
                    String endpoint = reader.readLine("Enter API endpoint: ");
                    ApiService.ApiResponse response = apiService.get(endpoint);
                    printApiResponse(response);
                }
                case "4" -> {
                    // Back to main menu
                }
                default -> System.out.println("Invalid option.");
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private void browseAndExecuteCommandSamples(ApiService apiService) {
        try {
            // Get commands directory
            File commandsDir = new File("commands");
            if (!commandsDir.exists() || !commandsDir.isDirectory()) {
                System.err.println("Commands directory not found. Expected: commands/");
                return;
            }

            // List available command types
            File[] commandDirs = commandsDir.listFiles(File::isDirectory);
            if (commandDirs == null || commandDirs.length == 0) {
                System.out.println("No command samples found.");
                return;
            }

            System.out.println("\n--- Available Commands ---");
            for (int i = 0; i < commandDirs.length; i++) {
                System.out.printf("%d. %s%n", i + 1, commandDirs[i].getName());
            }
            System.out.println((commandDirs.length + 1) + ". Back");

            String commandChoice = reader.readLine("\nSelect command type: ");
            int commandIndex = Integer.parseInt(commandChoice.trim()) - 1;

            if (commandIndex < 0 || commandIndex >= commandDirs.length) {
                return; // Back or invalid
            }

            File selectedCommandDir = commandDirs[commandIndex];
            String commandName = selectedCommandDir.getName();

            // List available samples for this command
            File[] sampleFiles = selectedCommandDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (sampleFiles == null || sampleFiles.length == 0) {
                System.out.println("No samples found for " + commandName);
                return;
            }

            System.out.println("\n--- Available Samples for " + commandName + " ---");
            for (int i = 0; i < sampleFiles.length; i++) {
                String sampleName = sampleFiles[i].getName().replace(".json", "");
                System.out.printf("%d. %s%n", i + 1, sampleName);
            }
            System.out.println((sampleFiles.length + 1) + ". Back");

            String sampleChoice = reader.readLine("\nSelect sample: ");
            int sampleIndex = Integer.parseInt(sampleChoice.trim()) - 1;

            if (sampleIndex < 0 || sampleIndex >= sampleFiles.length) {
                return; // Back or invalid
            }

            File selectedSample = sampleFiles[sampleIndex];

            // Display sample content
            String content = java.nio.file.Files.readString(selectedSample.toPath());
            System.out.println("\n" + "=".repeat(70));
            System.out.println("Sample: " + selectedSample.getName());
            System.out.println("=".repeat(70));

            try {
                Object json = objectMapper.readValue(content, Object.class);
                System.out.println(objectMapper.writeValueAsString(json));
            } catch (Exception e) {
                System.out.println(content);
            }

            System.out.println("=".repeat(70));

            // Ask if user wants to execute
            String execute = reader.readLine("\nExecute this command? (y/n): ");
            if ("y".equalsIgnoreCase(execute.trim())) {
                String prefix = reader.readLine("Enter idempotency prefix (or press Enter for default): ");

                ApiService.ApiResponse response = apiService.executeCommand(
                        commandName,
                        selectedSample,
                        prefix.isEmpty() ? null : prefix
                );

                printApiResponse(response);
            }

        } catch (NumberFormatException e) {
            System.err.println("Invalid selection.");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void mqMenu() {
        System.out.println("\n--- Message Queue Status ---");
        System.out.println("1. List all queues");
        System.out.println("2. Get queue status");
        System.out.println("3. Back to main menu");
        System.out.println();

        String choice = reader.readLine("Select option: ");

        try {
            MqService mqService = MqService.getInstance();
            switch (choice.trim()) {
                case "1" -> {
                    List<MqService.QueueInfo> queues = mqService.listQueues();
                    System.out.println("\nMessage Queues");
                    System.out.println("=".repeat(80));
                    System.out.printf("%-30s %-12s %-12s %-10s%n",
                            "Queue Name", "Messages", "Consumers", "Health");
                    System.out.println("-".repeat(80));

                    for (MqService.QueueInfo queue : queues) {
                        System.out.printf("%-30s %-12d %-12d %-10s%n",
                                queue.getName(),
                                queue.getMessageCount(),
                                queue.getConsumerCount(),
                                queue.isHealthy() ? "HEALTHY" : "UNHEALTHY");
                    }
                    System.out.println("=".repeat(80));
                }
                case "2" -> {
                    String queueName = reader.readLine("Enter queue name: ");
                    MqService.QueueInfo queue = mqService.getQueueStatus(queueName);

                    System.out.println("\nQueue: " + queue.getName());
                    System.out.println("=".repeat(50));
                    System.out.println("Messages: " + queue.getMessageCount());
                    System.out.println("Consumers: " + queue.getConsumerCount());
                    System.out.println("Health: " + (queue.isHealthy() ? "HEALTHY" : "UNHEALTHY"));
                }
                case "3" -> {
                    // Back to main menu
                }
                default -> System.out.println("Invalid option.");
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private void kafkaMenu() {
        System.out.println("\n--- Kafka Topics ---");
        System.out.println("1. List all topics");
        System.out.println("2. Get topic info");
        System.out.println("3. Show consumer lag");
        System.out.println("4. Back to main menu");
        System.out.println();

        String choice = reader.readLine("Select option: ");

        try {
            KafkaService kafkaService = KafkaService.getInstance();
            switch (choice.trim()) {
                case "1" -> {
                    List<KafkaService.TopicInfo> topics = kafkaService.listTopics();
                    System.out.println("\nKafka Topics");
                    System.out.println("=".repeat(80));
                    System.out.printf("%-30s %-12s %-18s %-15s%n",
                            "Topic Name", "Partitions", "Replication Factor", "Event Count");
                    System.out.println("-".repeat(80));

                    for (KafkaService.TopicInfo topic : topics) {
                        System.out.printf("%-30s %-12d %-18d %-15s%n",
                                topic.getName(),
                                topic.getPartitions(),
                                topic.getReplicationFactor(),
                                topic.getEventCount() >= 0 ? String.valueOf(topic.getEventCount()) : "N/A");
                    }
                    System.out.println("=".repeat(80));
                }
                case "2" -> {
                    String topicName = reader.readLine("Enter topic name: ");
                    KafkaService.TopicInfo topic = kafkaService.getTopicInfo(topicName);

                    System.out.println("\nTopic: " + topic.getName());
                    System.out.println("=".repeat(50));
                    System.out.println("Partitions: " + topic.getPartitions());
                    System.out.println("Replication Factor: " + topic.getReplicationFactor());
                    System.out.println("Event Count: " + (topic.getEventCount() >= 0 ? topic.getEventCount() : "N/A"));
                }
                case "3" -> {
                    String groupId = reader.readLine("Enter consumer group ID: ");
                    Map<String, KafkaService.ConsumerGroupLag> lagMap = kafkaService.getConsumerGroupLag(groupId);

                    if (lagMap.isEmpty()) {
                        System.out.println("No lag information found for consumer group: " + groupId);
                    } else {
                        System.out.println("\nConsumer Group Lag");
                        System.out.println("=".repeat(80));
                        System.out.printf("%-30s %-10s %-15s %-15s %-10s%n",
                                "Topic", "Partition", "Current Offset", "End Offset", "Lag");
                        System.out.println("-".repeat(80));

                        for (KafkaService.ConsumerGroupLag lag : lagMap.values()) {
                            System.out.printf("%-30s %-10d %-15d %-15d %-10d%n",
                                    lag.getTopic(),
                                    lag.getPartition(),
                                    lag.getCurrentOffset(),
                                    lag.getEndOffset(),
                                    lag.getLag());
                        }
                        System.out.println("=".repeat(80));
                    }
                }
                case "4" -> {
                    // Back to main menu
                }
                default -> System.out.println("Invalid option.");
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private void dockerMenu() {
        System.out.println("\n--- Docker Management ---");
        System.out.println("1. List containers");
        System.out.println("2. Execute command");
        System.out.println("3. View logs");
        System.out.println("4. Container stats");
        System.out.println("5. Back to main menu");
        System.out.println();

        String choice = reader.readLine("Select option: ");

        try {
            DockerService dockerService = DockerService.getInstance();
            switch (choice.trim()) {
                case "1" -> {
                    String showAll = reader.readLine("Show all containers? (y/n, default: n): ");
                    List<DockerService.ContainerInfo> containers = dockerService.listContainers(
                            "y".equalsIgnoreCase(showAll.trim())
                    );

                    System.out.println("\nDocker Containers");
                    System.out.println("=".repeat(100));
                    System.out.printf("%-15s %-25s %-30s %-12s%n",
                            "ID", "Name", "Image", "State");
                    System.out.println("-".repeat(100));

                    for (DockerService.ContainerInfo container : containers) {
                        System.out.printf("%-15s %-25s %-30s %-12s%n",
                                container.getId(),
                                container.getName(),
                                container.getImage(),
                                container.getState());
                    }
                    System.out.println("=".repeat(100));
                }
                case "2" -> {
                    String containerName = reader.readLine("Enter container name or ID: ");
                    String command = reader.readLine("Enter command to execute: ");

                    String output = dockerService.executeCommand(containerName, command);
                    System.out.println("\nCommand Output:");
                    System.out.println("=".repeat(60));
                    System.out.println(output);
                }
                case "3" -> {
                    String containerName = reader.readLine("Enter container name or ID: ");
                    String tailStr = reader.readLine("Number of lines (default: 100): ");
                    Integer tail = tailStr.isEmpty() ? 100 : Integer.parseInt(tailStr);

                    String logs = dockerService.getContainerLogs(containerName, tail);
                    System.out.println("\nContainer Logs:");
                    System.out.println("=".repeat(60));
                    System.out.println(logs);
                }
                case "4" -> {
                    String containerName = reader.readLine("Enter container name or ID: ");
                    DockerService.ContainerStats stats = dockerService.getContainerStats(containerName);

                    System.out.println("\nContainer Stats: " + stats.getContainer());
                    System.out.println("=".repeat(60));
                    System.out.printf("CPU Usage:     %.2f%%%n", stats.getCpuPercent());
                    System.out.printf("Memory Usage:  %s / %s (%.2f%%)%n",
                            formatBytes(stats.getMemoryUsage()),
                            formatBytes(stats.getMemoryLimit()),
                            stats.getMemoryPercent());
                }
                case "5" -> {
                    // Back to main menu
                }
                default -> System.out.println("Invalid option.");
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private void printTableData(PaginatedResult result) {
        if (result.getData().isEmpty()) {
            System.out.println("No data found.");
            return;
        }

        List<String> columns = List.copyOf(result.getData().get(0).keySet());
        Map<String, Integer> columnWidths = new java.util.HashMap<>();

        for (String column : columns) {
            int maxWidth = column.length();
            for (Map<String, Object> row : result.getData()) {
                Object value = row.get(column);
                int valueWidth = value != null ? value.toString().length() : 4;
                maxWidth = Math.max(maxWidth, valueWidth);
            }
            columnWidths.put(column, Math.min(maxWidth, 50));
        }

        // Print separator
        System.out.print("+");
        for (String column : columns) {
            System.out.print("-".repeat(columnWidths.get(column) + 2) + "+");
        }
        System.out.println();

        // Print header
        System.out.print("|");
        for (String column : columns) {
            System.out.printf(" %-" + columnWidths.get(column) + "s |", column);
        }
        System.out.println();

        // Print separator
        System.out.print("+");
        for (String column : columns) {
            System.out.print("-".repeat(columnWidths.get(column) + 2) + "+");
        }
        System.out.println();

        // Print rows
        for (Map<String, Object> row : result.getData()) {
            System.out.print("|");
            for (String column : columns) {
                Object value = row.get(column);
                String valueStr = value != null ? value.toString() : "null";
                if (valueStr.length() > columnWidths.get(column)) {
                    valueStr = valueStr.substring(0, columnWidths.get(column) - 3) + "...";
                }
                System.out.printf(" %-" + columnWidths.get(column) + "s |", valueStr);
            }
            System.out.println();
        }

        // Print separator
        System.out.print("+");
        for (String column : columns) {
            System.out.print("-".repeat(columnWidths.get(column) + 2) + "+");
        }
        System.out.println();

        System.out.printf("\nPage %d of %d (Total records: %d)\n",
                result.getPagination().getPage(),
                result.getPagination().getTotalPages(),
                result.getPagination().getTotalRecords());
    }

    private void printApiResponse(ApiService.ApiResponse response) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("API Response");
        System.out.println("=".repeat(60));
        System.out.println("Status Code: " + response.getStatusCode());
        System.out.println("Successful: " + (response.isSuccessful() ? "Yes" : "No"));
        if (response.getIdempotencyKey() != null) {
            System.out.println("Idempotency Key: " + response.getIdempotencyKey());
        }
        System.out.println("\nResponse Body:");
        System.out.println("-".repeat(60));

        try {
            Object json = objectMapper.readValue(response.getBody(), Object.class);
            System.out.println(objectMapper.writeValueAsString(json));
        } catch (Exception e) {
            System.out.println(response.getBody());
        }
        System.out.println("=".repeat(60));
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.2f %siB", bytes / Math.pow(1024, exp), pre);
    }
}
