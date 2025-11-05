package com.acme.platform.cli.commands;

import com.acme.platform.cli.service.MqService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Command(
        name = "mq",
        description = "Message Queue operations",
        subcommands = {
                MqCommands.ListQueues.class,
                MqCommands.QueueStatus.class
        }
)
public class MqCommands {

    @Command(name = "list", description = "List all message queues with their status")
    static class ListQueues implements Runnable {
        @Option(names = {"-f", "--format"}, description = "Output format: table or json (default: table)", defaultValue = "table")
        private String format;

        @Override
        public void run() {
            try (MqService mqService = MqService.getInstance()) {
                List<MqService.QueueInfo> queues = mqService.listQueues();

                if ("json".equalsIgnoreCase(format)) {
                    printJson(queues);
                } else {
                    printTable(queues);
                }
            } catch (Exception e) {
                System.err.println("Error listing queues: " + e.getMessage());
                System.exit(1);
            }
        }

        private void printJson(List<MqService.QueueInfo> queues) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                mapper.enable(SerializationFeature.INDENT_OUTPUT);

                List<Map<String, Object>> queueList = queues.stream()
                        .map(q -> {
                            Map<String, Object> queueMap = new java.util.HashMap<>();
                            queueMap.put("name", q.getName());
                            queueMap.put("messageCount", q.getMessageCount());
                            queueMap.put("consumerCount", q.getConsumerCount());
                            queueMap.put("status", q.getStatus());
                            queueMap.put("healthy", q.isHealthy());
                            return queueMap;
                        })
                        .collect(Collectors.toList());

                Map<String, Object> result = new java.util.HashMap<>();
                result.put("queues", queueList);
                System.out.println(mapper.writeValueAsString(result));
            } catch (Exception e) {
                System.err.println("Error formatting JSON: " + e.getMessage());
            }
        }

        private void printTable(List<MqService.QueueInfo> queues) {
            if (queues.isEmpty()) {
                System.out.println("No queues found.");
                return;
            }

            System.out.println("Message Queues");
            System.out.println("=".repeat(80));
            System.out.printf("%-30s %-12s %-12s %-10s %-10s%n",
                    "Queue Name", "Messages", "Consumers", "Status", "Health");
            System.out.println("-".repeat(80));

            for (MqService.QueueInfo queue : queues) {
                System.out.printf("%-30s %-12d %-12d %-10s %-10s%n",
                        queue.getName(),
                        queue.getMessageCount(),
                        queue.getConsumerCount(),
                        queue.getStatus(),
                        queue.isHealthy() ? "HEALTHY" : "UNHEALTHY");
            }

            System.out.println("=".repeat(80));
            System.out.println("Total queues: " + queues.size());
        }
    }

    @Command(name = "status", description = "Get status of a specific queue")
    static class QueueStatus implements Runnable {
        @Parameters(index = "0", description = "Queue name")
        private String queueName;

        @Option(names = {"-f", "--format"}, description = "Output format: table or json (default: table)", defaultValue = "table")
        private String format;

        @Override
        public void run() {
            try (MqService mqService = MqService.getInstance()) {
                MqService.QueueInfo queue = mqService.getQueueStatus(queueName);

                if ("json".equalsIgnoreCase(format)) {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.enable(SerializationFeature.INDENT_OUTPUT);

                    Map<String, Object> queueInfo = Map.of(
                            "name", queue.getName(),
                            "messageCount", queue.getMessageCount(),
                            "consumerCount", queue.getConsumerCount(),
                            "status", queue.getStatus(),
                            "healthy", queue.isHealthy()
                    );

                    System.out.println(mapper.writeValueAsString(queueInfo));
                } else {
                    System.out.println("Queue: " + queue.getName());
                    System.out.println("=".repeat(50));
                    System.out.println("Messages: " + queue.getMessageCount());
                    System.out.println("Consumers: " + queue.getConsumerCount());
                    System.out.println("Status: " + queue.getStatus());
                    System.out.println("Health: " + (queue.isHealthy() ? "HEALTHY" : "UNHEALTHY"));
                    System.out.println("=".repeat(50));
                }
            } catch (Exception e) {
                System.err.println("Error getting queue status: " + e.getMessage());
                System.exit(1);
            }
        }
    }
}
