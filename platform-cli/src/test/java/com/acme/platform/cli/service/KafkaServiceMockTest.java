package com.acme.platform.cli.service;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.internals.KafkaFutureImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaServiceMockTest {

    @Mock
    private AdminClient adminClient;

    private KafkaService kafkaService;

    @BeforeEach
    void setUp() throws Exception {
        kafkaService = KafkaService.getInstance();

        // Use reflection to inject mock admin client
        Field adminClientField = KafkaService.class.getDeclaredField("adminClient");
        adminClientField.setAccessible(true);
        adminClientField.set(kafkaService, adminClient);
    }

    @Test
    void testListTopics_returnsTopicList() throws Exception {
        // Arrange
        Set<String> topicNames = new HashSet<>(Arrays.asList("topic1", "topic2"));

        ListTopicsResult listTopicsResult = mock(ListTopicsResult.class);
        KafkaFutureImpl<Set<String>> namesFuture = new KafkaFutureImpl<>();
        namesFuture.complete(topicNames);
        when(listTopicsResult.names()).thenReturn(namesFuture);

        Node leader = new Node(1, "localhost", 9092);
        Node replica = new Node(1, "localhost", 9092);

        TopicPartitionInfo partition1 = new TopicPartitionInfo(0, leader,
                Collections.singletonList(replica), Collections.emptyList());
        TopicPartitionInfo partition2 = new TopicPartitionInfo(1, leader,
                Collections.singletonList(replica), Collections.emptyList());

        TopicDescription desc1 = new TopicDescription("topic1", false,
                Arrays.asList(partition1, partition2));
        TopicDescription desc2 = new TopicDescription("topic2", false,
                Collections.singletonList(partition1));

        Map<String, TopicDescription> descriptions = new HashMap<>();
        descriptions.put("topic1", desc1);
        descriptions.put("topic2", desc2);

        DescribeTopicsResult describeResult = mock(DescribeTopicsResult.class);
        KafkaFutureImpl<Map<String, TopicDescription>> descFuture = new KafkaFutureImpl<>();
        descFuture.complete(descriptions);
        when(describeResult.all()).thenReturn(descFuture);

        when(adminClient.listTopics()).thenReturn(listTopicsResult);
        when(adminClient.describeTopics(any(Collection.class))).thenReturn(describeResult);

        // Act
        List<KafkaService.TopicInfo> topics = kafkaService.listTopics();

        // Assert
        assertThat(topics).hasSize(2);

        KafkaService.TopicInfo topic1Info = topics.stream()
                .filter(t -> t.getName().equals("topic1"))
                .findFirst()
                .orElseThrow();

        assertThat(topic1Info.getName()).isEqualTo("topic1");
        assertThat(topic1Info.getPartitions()).isEqualTo(2);
        assertThat(topic1Info.getReplicationFactor()).isEqualTo(1);
        // Event count will be -1 because consumer creation fails in test
        assertThat(topic1Info.getEventCount()).isEqualTo(-1);

        verify(adminClient).listTopics();
        verify(adminClient).describeTopics(any(Collection.class));
    }

    @Test
    void testListTopics_withEmptyCluster_returnsEmptyList() throws Exception {
        // Arrange
        ListTopicsResult listTopicsResult = mock(ListTopicsResult.class);
        KafkaFutureImpl<Set<String>> namesFuture = new KafkaFutureImpl<>();
        namesFuture.complete(Collections.emptySet());
        when(listTopicsResult.names()).thenReturn(namesFuture);

        DescribeTopicsResult describeResult = mock(DescribeTopicsResult.class);
        KafkaFutureImpl<Map<String, TopicDescription>> descFuture = new KafkaFutureImpl<>();
        descFuture.complete(Collections.emptyMap());
        when(describeResult.all()).thenReturn(descFuture);

        when(adminClient.listTopics()).thenReturn(listTopicsResult);
        when(adminClient.describeTopics(any(Collection.class))).thenReturn(describeResult);

        // Act
        List<KafkaService.TopicInfo> topics = kafkaService.listTopics();

        // Assert
        assertThat(topics).isEmpty();
    }

    @Test
    void testGetTopicInfo_successful() throws Exception {
        // Arrange
        String topicName = "test-topic";
        Node leader = new Node(1, "localhost", 9092);
        Node replica = new Node(1, "localhost", 9092);

        TopicPartitionInfo partition = new TopicPartitionInfo(0, leader,
                Collections.singletonList(replica), Collections.emptyList());
        TopicDescription description = new TopicDescription(topicName, false,
                Collections.singletonList(partition));

        Map<String, TopicDescription> descriptions = new HashMap<>();
        descriptions.put(topicName, description);

        DescribeTopicsResult describeResult = mock(DescribeTopicsResult.class);
        KafkaFutureImpl<Map<String, TopicDescription>> descFuture = new KafkaFutureImpl<>();
        descFuture.complete(descriptions);
        when(describeResult.all()).thenReturn(descFuture);

        when(adminClient.describeTopics(Collections.singleton(topicName))).thenReturn(describeResult);

        // Act
        KafkaService.TopicInfo topicInfo = kafkaService.getTopicInfo(topicName);

        // Assert
        assertThat(topicInfo).isNotNull();
        assertThat(topicInfo.getName()).isEqualTo(topicName);
        assertThat(topicInfo.getPartitions()).isEqualTo(1);
        assertThat(topicInfo.getReplicationFactor()).isEqualTo(1);
        // Event count returns 0 or -1 depending on whether consumer can be created
        assertThat(topicInfo.getEventCount()).isLessThanOrEqualTo(0);

        verify(adminClient).describeTopics(Collections.singleton(topicName));
    }

    @Test
    void testGetTopicInfo_topicNotFound_throwsException() throws Exception {
        // Arrange
        String topicName = "non-existent-topic";

        DescribeTopicsResult describeResult = mock(DescribeTopicsResult.class);
        KafkaFutureImpl<Map<String, TopicDescription>> descFuture = new KafkaFutureImpl<>();
        descFuture.complete(Collections.emptyMap());
        when(describeResult.all()).thenReturn(descFuture);

        when(adminClient.describeTopics(Collections.singleton(topicName))).thenReturn(describeResult);

        // Act & Assert
        assertThatThrownBy(() -> kafkaService.getTopicInfo(topicName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Topic not found");
    }

    @Test
    void testGetConsumerGroupLag_successful() throws Exception {
        // Arrange
        String groupId = "test-group";

        ConsumerGroupDescription groupDescription = mock(ConsumerGroupDescription.class);
        Map<String, ConsumerGroupDescription> groupDescriptions = new HashMap<>();
        groupDescriptions.put(groupId, groupDescription);

        DescribeConsumerGroupsResult describeGroupsResult = mock(DescribeConsumerGroupsResult.class);
        KafkaFutureImpl<Map<String, ConsumerGroupDescription>> groupsFuture = new KafkaFutureImpl<>();
        groupsFuture.complete(groupDescriptions);
        when(describeGroupsResult.all()).thenReturn(groupsFuture);

        ListConsumerGroupOffsetsResult offsetsResult = mock(ListConsumerGroupOffsetsResult.class);
        KafkaFutureImpl<Map<TopicPartition, OffsetAndMetadata>> offsetsFuture = new KafkaFutureImpl<>();
        offsetsFuture.complete(Collections.emptyMap()); // Empty offsets to skip consumer creation

        when(offsetsResult.partitionsToOffsetAndMetadata()).thenReturn(offsetsFuture);

        when(adminClient.describeConsumerGroups(Collections.singleton(groupId)))
                .thenReturn(describeGroupsResult);
        when(adminClient.listConsumerGroupOffsets(groupId)).thenReturn(offsetsResult);

        // Act
        Map<String, KafkaService.ConsumerGroupLag> lagMap = kafkaService.getConsumerGroupLag(groupId);

        // Assert - will be empty because we provided empty offsets
        assertThat(lagMap).isEmpty();

        verify(adminClient).describeConsumerGroups(Collections.singleton(groupId));
        verify(adminClient).listConsumerGroupOffsets(groupId);
    }

    @Test
    void testGetConsumerGroupLag_groupNotFound_returnsEmptyMap() throws Exception {
        // Arrange
        String groupId = "non-existent-group";

        DescribeConsumerGroupsResult describeGroupsResult = mock(DescribeConsumerGroupsResult.class);
        KafkaFutureImpl<Map<String, ConsumerGroupDescription>> groupsFuture = new KafkaFutureImpl<>();
        groupsFuture.complete(Collections.emptyMap());
        when(describeGroupsResult.all()).thenReturn(groupsFuture);

        when(adminClient.describeConsumerGroups(Collections.singleton(groupId)))
                .thenReturn(describeGroupsResult);

        // Act
        Map<String, KafkaService.ConsumerGroupLag> lagMap = kafkaService.getConsumerGroupLag(groupId);

        // Assert
        assertThat(lagMap).isEmpty();
    }

    @Test
    void testTestConnection_successful() throws Exception {
        // Arrange
        ListTopicsResult listTopicsResult = mock(ListTopicsResult.class);
        KafkaFutureImpl<Set<String>> namesFuture = new KafkaFutureImpl<>();
        namesFuture.complete(Collections.emptySet());
        when(listTopicsResult.names()).thenReturn(namesFuture);
        when(adminClient.listTopics()).thenReturn(listTopicsResult);

        // Act & Assert
        assertThatCode(() -> kafkaService.testConnection()).doesNotThrowAnyException();
        verify(adminClient).listTopics();
    }

    @Test
    void testTestConnection_failsWithExecutionException() throws Exception {
        // Arrange
        ListTopicsResult listTopicsResult = mock(ListTopicsResult.class);
        KafkaFutureImpl<Set<String>> namesFuture = new KafkaFutureImpl<>();
        namesFuture.completeExceptionally(new RuntimeException("Connection failed"));
        when(listTopicsResult.names()).thenReturn(namesFuture);
        when(adminClient.listTopics()).thenReturn(listTopicsResult);

        // Act & Assert
        assertThatThrownBy(() -> kafkaService.testConnection())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void testClose_closesAdminClient() {
        // Act
        kafkaService.close();

        // Assert
        verify(adminClient).close(any());
    }

    @Test
    void testListTopics_withMultiplePartitions() throws Exception {
        // Arrange
        Set<String> topicNames = Collections.singleton("multi-partition-topic");

        ListTopicsResult listTopicsResult = mock(ListTopicsResult.class);
        KafkaFutureImpl<Set<String>> namesFuture = new KafkaFutureImpl<>();
        namesFuture.complete(topicNames);
        when(listTopicsResult.names()).thenReturn(namesFuture);

        Node leader = new Node(1, "localhost", 9092);
        Node replica1 = new Node(1, "localhost", 9092);
        Node replica2 = new Node(2, "localhost", 9093);

        TopicPartitionInfo partition1 = new TopicPartitionInfo(0, leader,
                Arrays.asList(replica1, replica2), Collections.emptyList());
        TopicPartitionInfo partition2 = new TopicPartitionInfo(1, leader,
                Arrays.asList(replica1, replica2), Collections.emptyList());
        TopicPartitionInfo partition3 = new TopicPartitionInfo(2, leader,
                Arrays.asList(replica1, replica2), Collections.emptyList());

        TopicDescription description = new TopicDescription("multi-partition-topic", false,
                Arrays.asList(partition1, partition2, partition3));

        Map<String, TopicDescription> descriptions = Collections.singletonMap("multi-partition-topic", description);

        DescribeTopicsResult describeResult = mock(DescribeTopicsResult.class);
        KafkaFutureImpl<Map<String, TopicDescription>> descFuture = new KafkaFutureImpl<>();
        descFuture.complete(descriptions);
        when(describeResult.all()).thenReturn(descFuture);

        when(adminClient.listTopics()).thenReturn(listTopicsResult);
        when(adminClient.describeTopics(any(Collection.class))).thenReturn(describeResult);

        // Act
        List<KafkaService.TopicInfo> topics = kafkaService.listTopics();

        // Assert
        assertThat(topics).hasSize(1);
        KafkaService.TopicInfo topicInfo = topics.get(0);
        assertThat(topicInfo.getName()).isEqualTo("multi-partition-topic");
        assertThat(topicInfo.getPartitions()).isEqualTo(3);
        assertThat(topicInfo.getReplicationFactor()).isEqualTo(2);
    }

    @Test
    void testGetTopicInfo_withExecutionException_throwsException() throws Exception {
        // Arrange
        String topicName = "test-topic";

        DescribeTopicsResult describeResult = mock(DescribeTopicsResult.class);
        KafkaFutureImpl<Map<String, TopicDescription>> descFuture = new KafkaFutureImpl<>();
        descFuture.completeExceptionally(new RuntimeException("Kafka error"));
        when(describeResult.all()).thenReturn(descFuture);

        when(adminClient.describeTopics(Collections.singleton(topicName))).thenReturn(describeResult);

        // Act & Assert
        assertThatThrownBy(() -> kafkaService.getTopicInfo(topicName))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void testGetConsumerGroupLag_withExecutionException_throwsException() throws Exception {
        // Arrange
        String groupId = "test-group";

        DescribeConsumerGroupsResult describeGroupsResult = mock(DescribeConsumerGroupsResult.class);
        KafkaFutureImpl<Map<String, ConsumerGroupDescription>> groupsFuture = new KafkaFutureImpl<>();
        groupsFuture.completeExceptionally(new RuntimeException("Kafka error"));
        when(describeGroupsResult.all()).thenReturn(groupsFuture);

        when(adminClient.describeConsumerGroups(Collections.singleton(groupId)))
                .thenReturn(describeGroupsResult);

        // Act & Assert
        assertThatThrownBy(() -> kafkaService.getConsumerGroupLag(groupId))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }
}
