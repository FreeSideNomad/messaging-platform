package com.acme.platform.cli.commands;

import com.acme.platform.cli.service.KafkaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Command(
        name = "kafka",
        description = "Kafka topic operations",
        subcommands = {
                KafkaCommands.ListTopics.class,
                KafkaCommands.TopicInfo.class,
                KafkaCommands.ConsumerLag.class
        }
)
public class KafkaCommands {

    @Command(name = "topics", description = "List all Kafka topics with event counts")
    static class ListTopics implements Runnable {
        @Option(names = {"-f", "--format"}, description = "Output format: table or json (default: table)", defaultValue = "table")
        private String format;

        @Override
        public void run() {
            try (KafkaService kafkaService = KafkaService.getInstance()) {
                List<KafkaService.TopicInfo> topics = kafkaService.listTopics();

                if ("json".equalsIgnoreCase(format)) {
                    printJson(topics);
                } else {
                    printTable(topics);
                }
            } catch (Exception e) {
                System.err.println("Error listing topics: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
        }

        private void printJson(List<KafkaService.TopicInfo> topics) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                mapper.enable(SerializationFeature.INDENT_OUTPUT);

                List<Map<String, Object>> topicList = topics.stream()
                        .map(t -> {
                            Map<String, Object> topicMap = new java.util.HashMap<>();
                            topicMap.put("name", t.getName());
                            topicMap.put("partitions", t.getPartitions());
                            topicMap.put("replicationFactor", t.getReplicationFactor());
                            topicMap.put("eventCount", t.getEventCount());
                            return topicMap;
                        })
                        .collect(Collectors.toList());

                Map<String, Object> result = new java.util.HashMap<>();
                result.put("topics", topicList);
                System.out.println(mapper.writeValueAsString(result));
            } catch (Exception e) {
                System.err.println("Error formatting JSON: " + e.getMessage());
            }
        }

        private void printTable(List<KafkaService.TopicInfo> topics) {
            if (topics.isEmpty()) {
                System.out.println("No topics found.");
                return;
            }

            System.out.println("Kafka Topics");
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
            System.out.println("Total topics: " + topics.size());
        }
    }

    @Command(name = "topic", description = "Get detailed information about a specific topic")
    static class TopicInfo implements Runnable {
        @Parameters(index = "0", description = "Topic name")
        private String topicName;

        @Option(names = {"-f", "--format"}, description = "Output format: table or json (default: table)", defaultValue = "table")
        private String format;

        @Override
        public void run() {
            try (KafkaService kafkaService = KafkaService.getInstance()) {
                KafkaService.TopicInfo topic = kafkaService.getTopicInfo(topicName);

                if ("json".equalsIgnoreCase(format)) {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.enable(SerializationFeature.INDENT_OUTPUT);

                    Map<String, Object> topicInfo = Map.of(
                            "name", topic.getName(),
                            "partitions", topic.getPartitions(),
                            "replicationFactor", topic.getReplicationFactor(),
                            "eventCount", topic.getEventCount()
                    );

                    System.out.println(mapper.writeValueAsString(topicInfo));
                } else {
                    System.out.println("Topic: " + topic.getName());
                    System.out.println("=".repeat(50));
                    System.out.println("Partitions: " + topic.getPartitions());
                    System.out.println("Replication Factor: " + topic.getReplicationFactor());
                    System.out.println("Event Count: " + (topic.getEventCount() >= 0 ? topic.getEventCount() : "N/A"));
                    System.out.println("=".repeat(50));
                }
            } catch (Exception e) {
                System.err.println("Error getting topic info: " + e.getMessage());
                System.exit(1);
            }
        }
    }

    @Command(name = "lag", description = "Show consumer group lag")
    static class ConsumerLag implements Runnable {
        @Parameters(index = "0", description = "Consumer group ID")
        private String groupId;

        @Option(names = {"-f", "--format"}, description = "Output format: table or json (default: table)", defaultValue = "table")
        private String format;

        @Override
        public void run() {
            try (KafkaService kafkaService = KafkaService.getInstance()) {
                Map<String, KafkaService.ConsumerGroupLag> lagMap = kafkaService.getConsumerGroupLag(groupId);

                if (lagMap.isEmpty()) {
                    System.out.println("No lag information found for consumer group: " + groupId);
                    return;
                }

                if ("json".equalsIgnoreCase(format)) {
                    printJson(lagMap);
                } else {
                    printTable(lagMap);
                }
            } catch (Exception e) {
                System.err.println("Error getting consumer lag: " + e.getMessage());
                System.exit(1);
            }
        }

        private void printJson(Map<String, KafkaService.ConsumerGroupLag> lagMap) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                mapper.enable(SerializationFeature.INDENT_OUTPUT);

                List<Map<String, Object>> lagList = lagMap.values().stream()
                        .map(l -> {
                            Map<String, Object> lagInfo = new java.util.HashMap<>();
                            lagInfo.put("topic", l.getTopic());
                            lagInfo.put("partition", l.getPartition());
                            lagInfo.put("currentOffset", l.getCurrentOffset());
                            lagInfo.put("endOffset", l.getEndOffset());
                            lagInfo.put("lag", l.getLag());
                            return lagInfo;
                        })
                        .collect(Collectors.toList());

                Map<String, Object> result = new java.util.HashMap<>();
                result.put("consumerLag", lagList);
                System.out.println(mapper.writeValueAsString(result));
            } catch (Exception e) {
                System.err.println("Error formatting JSON: " + e.getMessage());
            }
        }

        private void printTable(Map<String, KafkaService.ConsumerGroupLag> lagMap) {
            System.out.println("Consumer Group Lag");
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

            long totalLag = lagMap.values().stream().mapToLong(KafkaService.ConsumerGroupLag::getLag).sum();
            System.out.println("Total lag: " + totalLag);
        }
    }
}
