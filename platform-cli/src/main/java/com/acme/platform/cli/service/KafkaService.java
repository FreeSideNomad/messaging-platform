package com.acme.platform.cli.service;

import com.acme.platform.cli.config.CliConfiguration;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class KafkaService implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(KafkaService.class);
    private static KafkaService instance;
    private final AdminClient adminClient;
    private final CliConfiguration config;

    private KafkaService() {
        this.config = CliConfiguration.getInstance();
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafkaBootstrapServers());
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000);
        this.adminClient = AdminClient.create(props);
        logger.info("Kafka service initialized");
    }

    public static synchronized KafkaService getInstance() {
        if (instance == null) {
            instance = new KafkaService();
        }
        return instance;
    }

    public List<TopicInfo> listTopics() throws ExecutionException, InterruptedException {
        List<TopicInfo> topics = new ArrayList<>();

        ListTopicsResult listTopicsResult = adminClient.listTopics();
        Set<String> topicNames = listTopicsResult.names().get();

        DescribeTopicsResult describeResult = adminClient.describeTopics(topicNames);
        Map<String, TopicDescription> descriptions = describeResult.all().get();

        for (Map.Entry<String, TopicDescription> entry : descriptions.entrySet()) {
            String topicName = entry.getKey();
            TopicDescription description = entry.getValue();

            long eventCount = getTopicEventCount(topicName, description.partitions().size());

            topics.add(new TopicInfo(
                    topicName,
                    description.partitions().size(),
                    description.partitions().get(0).replicas().size(),
                    eventCount
            ));
        }

        return topics;
    }

    public TopicInfo getTopicInfo(String topicName) throws ExecutionException, InterruptedException {
        DescribeTopicsResult result = adminClient.describeTopics(Collections.singleton(topicName));
        Map<String, TopicDescription> descriptions = result.all().get();

        if (!descriptions.containsKey(topicName)) {
            throw new IllegalArgumentException("Topic not found: " + topicName);
        }

        TopicDescription description = descriptions.get(topicName);
        long eventCount = getTopicEventCount(topicName, description.partitions().size());

        return new TopicInfo(
                topicName,
                description.partitions().size(),
                description.partitions().get(0).replicas().size(),
                eventCount
        );
    }

    private long getTopicEventCount(String topicName, int partitionCount) {
        Properties props = new Properties();
        props.put("bootstrap.servers", config.getKafkaBootstrapServers());
        props.put("group.id", config.getKafkaConsumerGroup() + "-temp-" + UUID.randomUUID());
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());
        props.put("enable.auto.commit", "false");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            List<TopicPartition> partitions = new ArrayList<>();
            for (int i = 0; i < partitionCount; i++) {
                partitions.add(new TopicPartition(topicName, i));
            }

            Map<TopicPartition, Long> beginningOffsets = consumer.beginningOffsets(partitions);
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);

            long totalCount = 0;
            for (TopicPartition partition : partitions) {
                long beginning = beginningOffsets.getOrDefault(partition, 0L);
                long end = endOffsets.getOrDefault(partition, 0L);
                totalCount += (end - beginning);
            }

            return totalCount;
        } catch (Exception e) {
            logger.error("Error getting event count for topic {}", topicName, e);
            return -1; // Indicate error
        }
    }

    public Map<String, ConsumerGroupLag> getConsumerGroupLag(String groupId) throws ExecutionException, InterruptedException {
        Map<String, ConsumerGroupLag> lagMap = new HashMap<>();

        DescribeConsumerGroupsResult describeResult = adminClient.describeConsumerGroups(Collections.singleton(groupId));
        ConsumerGroupDescription description = describeResult.all().get().get(groupId);

        if (description == null) {
            return lagMap;
        }

        ListConsumerGroupOffsetsResult offsetsResult = adminClient.listConsumerGroupOffsets(groupId);
        Map<TopicPartition, OffsetAndMetadata> offsets = offsetsResult.partitionsToOffsetAndMetadata().get();

        // Get end offsets for all partitions
        Set<TopicPartition> partitions = offsets.keySet();
        if (!partitions.isEmpty()) {
            Properties props = new Properties();
            props.put("bootstrap.servers", config.getKafkaBootstrapServers());
            props.put("group.id", config.getKafkaConsumerGroup() + "-lag-" + UUID.randomUUID());
            props.put("key.deserializer", StringDeserializer.class.getName());
            props.put("value.deserializer", StringDeserializer.class.getName());

            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
                Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);

                for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : offsets.entrySet()) {
                    TopicPartition partition = entry.getKey();
                    long currentOffset = entry.getValue().offset();
                    long endOffset = endOffsets.getOrDefault(partition, 0L);
                    long lag = endOffset - currentOffset;

                    String key = partition.topic() + "-" + partition.partition();
                    lagMap.put(key, new ConsumerGroupLag(
                            partition.topic(),
                            partition.partition(),
                            currentOffset,
                            endOffset,
                            lag
                    ));
                }
            }
        }

        return lagMap;
    }

    public void testConnection() throws ExecutionException, InterruptedException {
        adminClient.listTopics().names().get();
        logger.info("Kafka connection test successful");
    }

    @Override
    public void close() {
        if (adminClient != null) {
            adminClient.close(Duration.ofSeconds(5));
            logger.info("Kafka admin client closed");
        }
    }

    public static class TopicInfo {
        private final String name;
        private final int partitions;
        private final int replicationFactor;
        private final long eventCount;

        public TopicInfo(String name, int partitions, int replicationFactor, long eventCount) {
            this.name = name;
            this.partitions = partitions;
            this.replicationFactor = replicationFactor;
            this.eventCount = eventCount;
        }

        public String getName() {
            return name;
        }

        public int getPartitions() {
            return partitions;
        }

        public int getReplicationFactor() {
            return replicationFactor;
        }

        public long getEventCount() {
            return eventCount;
        }
    }

    public static class ConsumerGroupLag {
        private final String topic;
        private final int partition;
        private final long currentOffset;
        private final long endOffset;
        private final long lag;

        public ConsumerGroupLag(String topic, int partition, long currentOffset, long endOffset, long lag) {
            this.topic = topic;
            this.partition = partition;
            this.currentOffset = currentOffset;
            this.endOffset = endOffset;
            this.lag = lag;
        }

        public String getTopic() {
            return topic;
        }

        public int getPartition() {
            return partition;
        }

        public long getCurrentOffset() {
            return currentOffset;
        }

        public long getEndOffset() {
            return endOffset;
        }

        public long getLag() {
            return lag;
        }
    }
}
