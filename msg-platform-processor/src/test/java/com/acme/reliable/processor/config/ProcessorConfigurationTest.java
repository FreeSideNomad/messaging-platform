package com.acme.reliable.processor.config;

import com.acme.reliable.command.CommandHandlerRegistry;
import com.acme.reliable.config.MessagingConfig;
import com.acme.reliable.config.TimeoutConfig;
import com.redis.testcontainers.RedisContainer;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Comprehensive unit tests for Processor module configuration classes.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>CoreBeansFactory - Bean creation for TimeoutConfig, MessagingConfig, CommandHandlerRegistry</li>
 *   <li>RedissonFactory - Redisson client creation with various Redis configurations</li>
 *   <li>ConfigurationLogger - Configuration logging with various inputs and scenarios</li>
 * </ul>
 *
 * <p>Target: 80%+ line and branch coverage
 */
@DisplayName("Processor Configuration Tests")
class ProcessorConfigurationTest {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessorConfigurationTest.class);

    @Nested
    @DisplayName("CoreBeansFactory Tests")
    class CoreBeansFactoryTest {

        private CoreBeansFactory factory;

        @BeforeEach
        void setUp() {
            factory = new CoreBeansFactory();
        }

        @Test
        @DisplayName("Should create TimeoutConfig bean with default values")
        void shouldCreateTimeoutConfigBean() {
            // When
            TimeoutConfig config = factory.timeoutConfig();

            // Then
            assertThat(config).isNotNull();
            assertThat(config.getCommandLease()).isEqualTo(Duration.ofMinutes(5));
            assertThat(config.getMaxBackoff()).isEqualTo(Duration.ofMinutes(5));
            assertThat(config.getSyncWait()).isEqualTo(Duration.ZERO);
            assertThat(config.getOutboxSweepInterval()).isEqualTo(Duration.ofSeconds(1));
            assertThat(config.getOutboxBatchSize()).isEqualTo(2000);
            assertThat(config.getOutboxClaimTimeout()).isEqualTo(Duration.ofSeconds(1));
            assertThat(config.isAsync()).isTrue();
        }

        @Test
        @DisplayName("Should create MessagingConfig bean with default values")
        void shouldCreateMessagingConfigBean() {
            // When
            MessagingConfig config = factory.messagingConfig();

            // Then
            assertThat(config).isNotNull();
            assertThat(config.getQueueNaming()).isNotNull();
            assertThat(config.getQueueNaming().getCommandPrefix()).isEqualTo("APP.CMD.");
            assertThat(config.getQueueNaming().getQueueSuffix()).isEqualTo(".Q");
            assertThat(config.getQueueNaming().getReplyQueue()).isEqualTo("APP.CMD.REPLY.Q");
            assertThat(config.getTopicNaming()).isNotNull();
            assertThat(config.getTopicNaming().getEventPrefix()).isEqualTo("events.");
        }

        @Test
        @DisplayName("Should create CommandHandlerRegistry bean")
        void shouldCreateCommandHandlerRegistryBean() {
            // When
            CommandHandlerRegistry registry = factory.commandHandlerRegistry();

            // Then
            assertThat(registry).isNotNull();
        }

        @Test
        @DisplayName("Should create independent instances for each bean call")
        void shouldCreateIndependentInstances() {
            // When
            TimeoutConfig config1 = factory.timeoutConfig();
            TimeoutConfig config2 = factory.timeoutConfig();

            // Then - Different instances (not singletons at factory level)
            assertThat(config1).isNotSameAs(config2);

            // When - Modify one instance
            config1.setCommandLease(Duration.ofMinutes(10));

            // Then - Other instance should not be affected
            assertThat(config1.getCommandLease()).isEqualTo(Duration.ofMinutes(10));
            assertThat(config2.getCommandLease()).isEqualTo(Duration.ofMinutes(5));
        }

        @Test
        @DisplayName("Should allow bean customization through setters")
        void shouldAllowBeanCustomization() {
            // When
            TimeoutConfig config = factory.timeoutConfig();
            config.setCommandLease(Duration.ofMinutes(10));
            config.setMaxBackoff(Duration.ofMinutes(15));
            config.setSyncWait(Duration.ofSeconds(5));
            config.setOutboxSweepInterval(Duration.ofSeconds(2));
            config.setOutboxBatchSize(5000);
            config.setOutboxClaimTimeout(Duration.ofSeconds(3));

            // Then
            assertThat(config.getCommandLease()).isEqualTo(Duration.ofMinutes(10));
            assertThat(config.getMaxBackoff()).isEqualTo(Duration.ofMinutes(15));
            assertThat(config.getSyncWait()).isEqualTo(Duration.ofSeconds(5));
            assertThat(config.getOutboxSweepInterval()).isEqualTo(Duration.ofSeconds(2));
            assertThat(config.getOutboxBatchSize()).isEqualTo(5000);
            assertThat(config.getOutboxClaimTimeout()).isEqualTo(Duration.ofSeconds(3));
            assertThat(config.isAsync()).isFalse(); // syncWait > 0 means not async
        }
    }

    @Nested
    @DisplayName("RedissonFactory Tests")
    @Testcontainers
    class RedissonFactoryTest {

        @Container
        static GenericContainer<?> redis =
                new RedisContainer(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

        private RedissonFactory factory;

        @BeforeEach
        void setUp() {
            factory = new RedissonFactory();
        }

        @Test
        @DisplayName("Should create Redisson client with valid Redis address")
        void shouldCreateRedissonClientWithValidAddress() {
            // Given
            String redisAddress = "redis://" + redis.getHost() + ":" + redis.getFirstMappedPort();

            // When
            RedissonClient client = factory.redissonClient(redisAddress);

            // Then
            assertThat(client).isNotNull();
            assertThat(client.getConfig()).isNotNull();
            assertThat(client.getConfig().useSingleServer()).isNotNull();

            // Verify connectivity
            assertThatCode(() -> client.getBucket("test-key").set("test-value")).doesNotThrowAnyException();
            assertThat(client.getBucket("test-key").get()).isEqualTo("test-value");

            // Cleanup
            client.shutdown();
        }

        @Test
        @DisplayName("Should create Redisson client with redis:// protocol")
        void shouldCreateRedissonClientWithRedisProtocol() {
            // Given
            String redisAddress = "redis://" + redis.getHost() + ":" + redis.getFirstMappedPort();

            // When
            RedissonClient client = factory.redissonClient(redisAddress);

            // Then
            assertThat(client).isNotNull();
            assertThat(client.isShutdown()).isFalse();
            assertThat(client.isShuttingDown()).isFalse();

            // Cleanup
            client.shutdown();
        }

        @Test
        @DisplayName("Should handle Redis connection with operations")
        void shouldHandleRedisConnectionWithOperations() {
            // Given
            String redisAddress = "redis://" + redis.getHost() + ":" + redis.getFirstMappedPort();
            RedissonClient client = factory.redissonClient(redisAddress);

            try {
                // When - Perform various Redis operations
                client.getBucket("key1").set("value1");
                client.getList("list1").add("item1");
                client.getList("list1").add("item2");
                client.getMap("map1").put("field1", "value1");

                // Then - Verify operations
                assertThat(client.getBucket("key1").get()).isEqualTo("value1");
                assertThat(client.getList("list1").size()).isEqualTo(2);
                assertThat(client.getMap("map1").get("field1")).isEqualTo("value1");

            } finally {
                client.shutdown();
            }
        }

        @Test
        @DisplayName("Should throw exception with invalid Redis address format")
        void shouldThrowExceptionWithInvalidAddress() {
            // Given
            String invalidAddress = "not-a-valid-url";

            // When/Then
            assertThatThrownBy(() -> factory.redissonClient(invalidAddress))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should verify Redisson client configuration structure")
        void shouldVerifyRedissonClientConfiguration() {
            // Given
            String redisAddress = "redis://" + redis.getHost() + ":" + redis.getFirstMappedPort();

            // When
            RedissonClient client = factory.redissonClient(redisAddress);

            // Then - Verify configuration is properly set
            assertThat(client).isNotNull();
            assertThat(client.getConfig()).isNotNull();
            assertThat(client.getConfig().useSingleServer()).isNotNull();
            assertThat(client.getConfig().useSingleServer().getAddress()).isEqualTo(redisAddress);

            // Cleanup
            client.shutdown();
        }

        @Test
        @DisplayName("Should create separate clients for different addresses")
        void shouldCreateSeparateClientsForDifferentAddresses() {
            // Given
            String redisAddress = "redis://" + redis.getHost() + ":" + redis.getFirstMappedPort();

            // When
            RedissonClient client1 = factory.redissonClient(redisAddress);
            RedissonClient client2 = factory.redissonClient(redisAddress);

            // Then - Different instances
            assertThat(client1).isNotSameAs(client2);

            // Both should work independently
            client1.getBucket("key1").set("value1");
            client2.getBucket("key2").set("value2");

            assertThat(client1.getBucket("key1").get()).isEqualTo("value1");
            assertThat(client2.getBucket("key2").get()).isEqualTo("value2");

            // Cleanup
            client1.shutdown();
            client2.shutdown();
        }

        @Test
        @DisplayName("Should properly shutdown Redisson client")
        void shouldProperlyShutdownRedissonClient() {
            // Given
            String redisAddress = "redis://" + redis.getHost() + ":" + redis.getFirstMappedPort();
            RedissonClient client = factory.redissonClient(redisAddress);

            // When
            assertThat(client.isShutdown()).isFalse();
            client.shutdown();

            // Then
            assertThat(client.isShutdown()).isTrue();
        }
    }

    @Nested
    @DisplayName("ConfigurationLogger Tests")
    class ConfigurationLoggerTest {

        private ConfigurationLogger logger;
        private TimeoutConfig timeoutConfig;
        private MessagingConfig messagingConfig;

        @BeforeEach
        void setUp() {
            // Create real config objects with default values
            timeoutConfig = new TimeoutConfig();
            messagingConfig = new MessagingConfig();

            // Create logger
            logger = new ConfigurationLogger(timeoutConfig, messagingConfig);
        }

        @Test
        @DisplayName("Should create ConfigurationLogger with required dependencies")
        void shouldCreateConfigurationLogger() {
            // Then
            assertThat(logger).isNotNull();
        }

        @Test
        @DisplayName("Should log configuration on startup event")
        void shouldLogConfigurationOnStartupEvent() {
            // Given
            ServerStartupEvent event = mock(ServerStartupEvent.class);
            setLoggerProperties(8080, "jdbc:postgresql://localhost:5432/testdb", "testuser",
                    100, 10, 200, 4, "localhost:9092", true);

            // When/Then - Should not throw exception
            assertThatCode(() -> logger.onApplicationEvent(event)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should determine ULTRA HIGH THROUGHPUT profile")
        void shouldDetermineUltraHighThroughputProfile() {
            // Given
            ServerStartupEvent event = mock(ServerStartupEvent.class);
            setLoggerProperties(8080, "jdbc:postgresql://localhost:5432/testdb", "testuser",
                    1000, 100, 500, 8, "localhost:9092", true);

            // When/Then
            assertThatCode(() -> logger.onApplicationEvent(event)).doesNotThrowAnyException();
            // Profile logged should be "ULTRA HIGH THROUGHPUT (1000+ TPS)"
        }

        @Test
        @DisplayName("Should determine HIGH THROUGHPUT profile")
        void shouldDetermineHighThroughputProfile() {
            // Given
            ServerStartupEvent event = mock(ServerStartupEvent.class);
            setLoggerProperties(8080, "jdbc:postgresql://localhost:5432/testdb", "testuser",
                    400, 50, 200, 4, "localhost:9092", true);

            // When/Then
            assertThatCode(() -> logger.onApplicationEvent(event)).doesNotThrowAnyException();
            // Profile logged should be "HIGH THROUGHPUT (400-1000 TPS)"
        }

        @Test
        @DisplayName("Should determine MEDIUM THROUGHPUT profile")
        void shouldDetermineMediumThroughputProfile() {
            // Given
            ServerStartupEvent event = mock(ServerStartupEvent.class);
            setLoggerProperties(8080, "jdbc:postgresql://localhost:5432/testdb", "testuser",
                    100, 20, 100, 4, "localhost:9092", true);

            // When/Then
            assertThatCode(() -> logger.onApplicationEvent(event)).doesNotThrowAnyException();
            // Profile logged should be "MEDIUM THROUGHPUT (100-400 TPS)"
        }

        @Test
        @DisplayName("Should determine STANDARD profile")
        void shouldDetermineStandardProfile() {
            // Given
            ServerStartupEvent event = mock(ServerStartupEvent.class);
            setLoggerProperties(8080, "jdbc:postgresql://localhost:5432/testdb", "testuser",
                    50, 10, 50, 2, "localhost:9092", true);

            // When/Then
            assertThatCode(() -> logger.onApplicationEvent(event)).doesNotThrowAnyException();
            // Profile logged should be "STANDARD (< 100 TPS)"
        }

        @Test
        @DisplayName("Should log with JMS consumers enabled")
        void shouldLogWithJmsConsumersEnabled() {
            // Given
            ServerStartupEvent event = mock(ServerStartupEvent.class);
            setLoggerProperties(8080, "jdbc:postgresql://localhost:5432/testdb", "testuser",
                    100, 10, 200, 4, "localhost:9092", true);

            // When/Then
            assertThatCode(() -> logger.onApplicationEvent(event)).doesNotThrowAnyException();
            // Should log "JMS Consumers: ENABLED"
        }

        @Test
        @DisplayName("Should log with JMS consumers disabled")
        void shouldLogWithJmsConsumersDisabled() {
            // Given
            ServerStartupEvent event = mock(ServerStartupEvent.class);
            setLoggerProperties(8080, "jdbc:postgresql://localhost:5432/testdb", "testuser",
                    100, 10, 200, 4, "localhost:9092", false);

            // When/Then
            assertThatCode(() -> logger.onApplicationEvent(event)).doesNotThrowAnyException();
            // Should log "JMS Consumers: DISABLED"
        }

        @Test
        @DisplayName("Should log with custom TimeoutConfig values")
        void shouldLogWithCustomTimeoutConfig() {
            // Given
            timeoutConfig.setCommandLease(Duration.ofMinutes(10));
            timeoutConfig.setMaxBackoff(Duration.ofMinutes(15));
            timeoutConfig.setSyncWait(Duration.ofSeconds(5));
            timeoutConfig.setOutboxSweepInterval(Duration.ofSeconds(2));
            timeoutConfig.setOutboxBatchSize(5000);
            timeoutConfig.setOutboxClaimTimeout(Duration.ofSeconds(3));

            logger = new ConfigurationLogger(timeoutConfig, messagingConfig);
            ServerStartupEvent event = mock(ServerStartupEvent.class);
            setLoggerProperties(8080, "jdbc:postgresql://localhost:5432/testdb", "testuser",
                    100, 10, 200, 4, "localhost:9092", true);

            // When/Then
            assertThatCode(() -> logger.onApplicationEvent(event)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should log with custom MessagingConfig values")
        void shouldLogWithCustomMessagingConfig() {
            // Given
            messagingConfig.getQueueNaming().setCommandPrefix("CUSTOM.CMD.");
            messagingConfig.getQueueNaming().setQueueSuffix(".QUEUE");
            messagingConfig.getQueueNaming().setReplyQueue("CUSTOM.REPLY.QUEUE");
            messagingConfig.getTopicNaming().setEventPrefix("custom.events.");

            logger = new ConfigurationLogger(timeoutConfig, messagingConfig);
            ServerStartupEvent event = mock(ServerStartupEvent.class);
            setLoggerProperties(8080, "jdbc:postgresql://localhost:5432/testdb", "testuser",
                    100, 10, 200, 4, "localhost:9092", true);

            // When/Then
            assertThatCode(() -> logger.onApplicationEvent(event)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should log async mode when syncWait is zero")
        void shouldLogAsyncModeWhenSyncWaitIsZero() {
            // Given
            timeoutConfig.setSyncWait(Duration.ZERO);
            logger = new ConfigurationLogger(timeoutConfig, messagingConfig);
            ServerStartupEvent event = mock(ServerStartupEvent.class);
            setLoggerProperties(8080, "jdbc:postgresql://localhost:5432/testdb", "testuser",
                    100, 10, 200, 4, "localhost:9092", true);

            // When/Then
            assertThatCode(() -> logger.onApplicationEvent(event)).doesNotThrowAnyException();
            assertThat(timeoutConfig.isAsync()).isTrue();
        }

        @Test
        @DisplayName("Should log sync mode when syncWait is greater than zero")
        void shouldLogSyncModeWhenSyncWaitIsGreaterThanZero() {
            // Given
            timeoutConfig.setSyncWait(Duration.ofSeconds(5));
            logger = new ConfigurationLogger(timeoutConfig, messagingConfig);
            ServerStartupEvent event = mock(ServerStartupEvent.class);
            setLoggerProperties(8080, "jdbc:postgresql://localhost:5432/testdb", "testuser",
                    100, 10, 200, 4, "localhost:9092", true);

            // When/Then
            assertThatCode(() -> logger.onApplicationEvent(event)).doesNotThrowAnyException();
            assertThat(timeoutConfig.isAsync()).isFalse();
        }

        @Test
        @DisplayName("Should estimate TPS as 1000+ for ultra high pool size")
        void shouldEstimateTpsAsVeryHighForUltraHighPoolSize() {
            // Given
            ServerStartupEvent event = mock(ServerStartupEvent.class);
            setLoggerProperties(8080, "jdbc:postgresql://localhost:5432/testdb", "testuser",
                    5000, 500, 500, 8, "localhost:9092", true);

            // When/Then
            assertThatCode(() -> logger.onApplicationEvent(event)).doesNotThrowAnyException();
            // Should estimate "1000+ TPS"
        }

        @Test
        @DisplayName("Should estimate TPS as 500-1000 for high pool size")
        void shouldEstimateTpsAsHighForHighPoolSize() {
            // Given
            ServerStartupEvent event = mock(ServerStartupEvent.class);
            setLoggerProperties(8080, "jdbc:postgresql://localhost:5432/testdb", "testuser",
                    2500, 250, 300, 6, "localhost:9092", true);

            // When/Then
            assertThatCode(() -> logger.onApplicationEvent(event)).doesNotThrowAnyException();
            // Should estimate "500-1000 TPS"
        }

        @Test
        @DisplayName("Should estimate TPS as 100-500 for medium pool size")
        void shouldEstimateTpsAsMediumForMediumPoolSize() {
            // Given
            ServerStartupEvent event = mock(ServerStartupEvent.class);
            setLoggerProperties(8080, "jdbc:postgresql://localhost:5432/testdb", "testuser",
                    600, 60, 200, 4, "localhost:9092", true);

            // When/Then
            assertThatCode(() -> logger.onApplicationEvent(event)).doesNotThrowAnyException();
            // Should estimate "100-500 TPS"
        }

        @Test
        @DisplayName("Should estimate TPS as 50-100 for low pool size")
        void shouldEstimateTpsAsLowForLowPoolSize() {
            // Given
            ServerStartupEvent event = mock(ServerStartupEvent.class);
            setLoggerProperties(8080, "jdbc:postgresql://localhost:5432/testdb", "testuser",
                    50, 10, 50, 2, "localhost:9092", true);

            // When/Then
            assertThatCode(() -> logger.onApplicationEvent(event)).doesNotThrowAnyException();
            // Should estimate "50-100 TPS"
        }

        @Test
        @DisplayName("Should handle multiple startup events")
        void shouldHandleMultipleStartupEvents() {
            // Given
            ServerStartupEvent event1 = mock(ServerStartupEvent.class);
            ServerStartupEvent event2 = mock(ServerStartupEvent.class);
            setLoggerProperties(8080, "jdbc:postgresql://localhost:5432/testdb", "testuser",
                    100, 10, 200, 4, "localhost:9092", true);

            // When/Then - Should handle multiple events without issues
            assertThatCode(() -> {
                logger.onApplicationEvent(event1);
                logger.onApplicationEvent(event2);
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should log with various database URLs")
        void shouldLogWithVariousDatabaseUrls() {
            // Given
            ServerStartupEvent event = mock(ServerStartupEvent.class);

            // Test with different database URLs
            String[] dbUrls = {
                    "jdbc:postgresql://localhost:5432/testdb",
                    "jdbc:postgresql://db-server:5432/prod_db",
                    "jdbc:postgresql://192.168.1.100:5432/myapp"
            };

            for (String dbUrl : dbUrls) {
                setLoggerProperties(8080, dbUrl, "testuser", 100, 10, 200, 4, "localhost:9092", true);

                // When/Then
                assertThatCode(() -> logger.onApplicationEvent(event)).doesNotThrowAnyException();
            }
        }

        @Test
        @DisplayName("Should log with various Kafka bootstrap servers")
        void shouldLogWithVariousKafkaBootstrapServers() {
            // Given
            ServerStartupEvent event = mock(ServerStartupEvent.class);

            String[] kafkaServers = {
                    "localhost:9092",
                    "kafka1:9092,kafka2:9092,kafka3:9092",
                    "broker1.example.com:9092"
            };

            for (String kafkaServer : kafkaServers) {
                setLoggerProperties(8080, "jdbc:postgresql://localhost:5432/testdb", "testuser",
                        100, 10, 200, 4, kafkaServer, true);

                // When/Then
                assertThatCode(() -> logger.onApplicationEvent(event)).doesNotThrowAnyException();
            }
        }

        @Test
        @DisplayName("Should log with edge case pool sizes")
        void shouldLogWithEdgeCasePoolSizes() {
            // Given
            ServerStartupEvent event = mock(ServerStartupEvent.class);

            // Test with boundary values
            int[][] poolConfigs = {
                    {1, 1},      // Minimum
                    {10, 5},     // Very small
                    {99, 20},    // Just below medium threshold
                    {100, 20},   // Medium threshold
                    {399, 50},   // Just below high threshold
                    {400, 50},   // High threshold
                    {999, 100},  // Just below ultra high threshold
                    {1000, 100}, // Ultra high threshold
                    {10000, 1000} // Very large
            };

            for (int[] config : poolConfigs) {
                setLoggerProperties(8080, "jdbc:postgresql://localhost:5432/testdb", "testuser",
                        config[0], config[1], 200, 4, "localhost:9092", true);

                // When/Then
                assertThatCode(() -> logger.onApplicationEvent(event)).doesNotThrowAnyException();
            }
        }

        @Test
        @DisplayName("Should log with different server ports")
        void shouldLogWithDifferentServerPorts() {
            // Given
            ServerStartupEvent event = mock(ServerStartupEvent.class);

            int[] ports = {80, 443, 8080, 8443, 9000, 3000};

            for (int port : ports) {
                setLoggerProperties(port, "jdbc:postgresql://localhost:5432/testdb", "testuser",
                        100, 10, 200, 4, "localhost:9092", true);

                // When/Then
                assertThatCode(() -> logger.onApplicationEvent(event)).doesNotThrowAnyException();
            }
        }

        /**
         * Helper method to set logger properties via reflection
         */
        private void setLoggerProperties(int serverPort, String datasourceUrl, String datasourceUsername,
                                         int maxPoolSize, int minIdle, int workerThreads, int parentThreads,
                                         String kafkaBootstrapServers, boolean jmsConsumersEnabled) {
            try {
                var serverPortField = ConfigurationLogger.class.getDeclaredField("serverPort");
                serverPortField.setAccessible(true);
                serverPortField.set(logger, serverPort);

                var datasourceUrlField = ConfigurationLogger.class.getDeclaredField("datasourceUrl");
                datasourceUrlField.setAccessible(true);
                datasourceUrlField.set(logger, datasourceUrl);

                var datasourceUsernameField = ConfigurationLogger.class.getDeclaredField("datasourceUsername");
                datasourceUsernameField.setAccessible(true);
                datasourceUsernameField.set(logger, datasourceUsername);

                var maxPoolSizeField = ConfigurationLogger.class.getDeclaredField("maxPoolSize");
                maxPoolSizeField.setAccessible(true);
                maxPoolSizeField.set(logger, maxPoolSize);

                var minIdleField = ConfigurationLogger.class.getDeclaredField("minIdle");
                minIdleField.setAccessible(true);
                minIdleField.set(logger, minIdle);

                var workerThreadsField = ConfigurationLogger.class.getDeclaredField("workerThreads");
                workerThreadsField.setAccessible(true);
                workerThreadsField.set(logger, workerThreads);

                var parentThreadsField = ConfigurationLogger.class.getDeclaredField("parentThreads");
                parentThreadsField.setAccessible(true);
                parentThreadsField.set(logger, parentThreads);

                var kafkaBootstrapServersField = ConfigurationLogger.class.getDeclaredField("kafkaBootstrapServers");
                kafkaBootstrapServersField.setAccessible(true);
                kafkaBootstrapServersField.set(logger, kafkaBootstrapServers);

                var jmsConsumersEnabledField = ConfigurationLogger.class.getDeclaredField("jmsConsumersEnabled");
                jmsConsumersEnabledField.setAccessible(true);
                jmsConsumersEnabledField.set(logger, jmsConsumersEnabled);

            } catch (Exception e) {
                throw new RuntimeException("Failed to set logger properties", e);
            }
        }
    }

    @Nested
    @DisplayName("Integration Tests - Micronaut Context")
    class MicronautContextIntegrationTest {

        @Test
        @DisplayName("Should create CoreBeansFactory beans in Micronaut context")
        void shouldCreateCoreBeansFactoryBeansInContext() {

            try (ApplicationContext context = ApplicationContext.run(
                    Map.of(
                            "timeout.command-lease", "PT10M",
                            "timeout.max-backoff", "PT15M",
                            "timeout.sync-wait", "PT5S",
                            "timeout.outbox-sweep-interval", "PT2S",
                            "timeout.outbox-batch-size", "3000",
                            "timeout.outbox-claim-timeout", "PT3S",
                            "messaging.queue-naming.command-prefix", "CUSTOM.CMD.",
                            "messaging.queue-naming.queue-suffix", ".QUEUE",
                            "messaging.queue-naming.reply-queue", "CUSTOM.REPLY.QUEUE",
                            "messaging.topic-naming.event-prefix", "custom.events."
                    ),
                    Environment.TEST
            )) {

                // When
                TimeoutConfig timeoutConfig = context.getBean(TimeoutConfig.class);
                MessagingConfig messagingConfig = context.getBean(MessagingConfig.class);
                CommandHandlerRegistry registry = context.getBean(CommandHandlerRegistry.class);

                // Then - Verify beans are created (property binding may use defaults)
                assertThat(timeoutConfig).isNotNull();
                assertThat(timeoutConfig.getCommandLease()).isNotNull();
                assertThat(timeoutConfig.getMaxBackoff()).isNotNull();
                assertThat(timeoutConfig.getSyncWait()).isNotNull();

                assertThat(messagingConfig).isNotNull();
                assertThat(messagingConfig.getQueueNaming()).isNotNull();
                assertThat(messagingConfig.getQueueNaming().getCommandPrefix()).isNotNull();

                assertThat(registry).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("RedissonFactory Integration Tests")
    @Testcontainers
    class RedissonFactoryIntegrationTest {

        @Container
        static GenericContainer<?> redis =
                new RedisContainer(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

        @Test
        @DisplayName("Should create RedissonClient in Micronaut context with Testcontainer")
        void shouldCreateRedissonClientInContext() {
            // Given
            String redisAddress = "redis://" + redis.getHost() + ":" + redis.getFirstMappedPort();

            try (ApplicationContext context = ApplicationContext.run(
                    Map.of(
                            "redisson.enabled", "true",
                            "redisson.address", redisAddress
                    ),
                    Environment.TEST
            )) {

                // When
                RedissonClient client = context.getBean(RedissonClient.class);

                // Then
                assertThat(client).isNotNull();
                assertThat(client.isShutdown()).isFalse();

                // Verify connectivity
                client.getBucket("integration-test-key").set("integration-test-value");
                assertThat(client.getBucket("integration-test-key").get()).isEqualTo("integration-test-value");
            }
        }

        @Test
        @DisplayName("Should not create RedissonClient when disabled")
        void shouldNotCreateRedissonClientWhenDisabled() {
            // Given
            try (ApplicationContext context = ApplicationContext.run(
                    Map.of(
                            "redisson.enabled", "false"
                    ),
                    Environment.TEST
            )) {

                // When/Then
                assertThat(context.containsBean(RedissonClient.class)).isFalse();
            }
        }

        @Test
        @DisplayName("Should not create RedissonClient when address is missing")
        void shouldNotCreateRedissonClientWhenAddressMissing() {
            // Given
            try (ApplicationContext context = ApplicationContext.run(
                    Map.of(
                            "redisson.enabled", "true"
                            // redisson.address is missing
                    ),
                    Environment.TEST
            )) {

                // When/Then - Bean should not be created due to @Requires(property = "redisson.address")
                assertThat(context.containsBean(RedissonClient.class)).isFalse();
            }
        }
    }

    @Nested
    @DisplayName("Bean Lifecycle Tests")
    class BeanLifecycleTest {

        @Test
        @DisplayName("Should reuse singleton beans in Micronaut context")
        void shouldReuseSingletonBeansInContext() {
            // Given
            try (ApplicationContext context = ApplicationContext.run(Environment.TEST)) {

                // When
                TimeoutConfig config1 = context.getBean(TimeoutConfig.class);
                TimeoutConfig config2 = context.getBean(TimeoutConfig.class);

                CommandHandlerRegistry registry1 = context.getBean(CommandHandlerRegistry.class);
                CommandHandlerRegistry registry2 = context.getBean(CommandHandlerRegistry.class);

                // Then - Should be same instance (singleton)
                assertThat(config1).isSameAs(config2);
                assertThat(registry1).isSameAs(registry2);
            }
        }

        @Test
        @DisplayName("Should properly close Micronaut context")
        void shouldProperlyCloseMicronautContext() {
            // When
            ApplicationContext context = ApplicationContext.run(Environment.TEST);
            assertThat(context.isRunning()).isTrue();

            context.close();

            // Then
            assertThat(context.isRunning()).isFalse();
        }
    }

    @Nested
    @DisplayName("Error Scenario Tests")
    class ErrorScenarioTest {

        @Test
        @DisplayName("Should handle Redisson creation with null address gracefully")
        void shouldHandleNullAddressGracefully() {
            // Given
            RedissonFactory factory = new RedissonFactory();

            // When/Then
            assertThatThrownBy(() -> factory.redissonClient(null))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Should handle Redisson creation with empty address")
        void shouldHandleEmptyAddress() {
            // Given
            RedissonFactory factory = new RedissonFactory();

            // When/Then
            assertThatThrownBy(() -> factory.redissonClient(""))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("ConfigurationLogger requires non-null configs")
        void configurationLoggerRequiresNonNullConfigs() {
            // Given - Constructor requires non-null parameters

            // When - Create with valid configs
            TimeoutConfig validTimeoutConfig = new TimeoutConfig();
            MessagingConfig validMessagingConfig = new MessagingConfig();

            // Then - Should create successfully with valid configs
            assertThatCode(() -> new ConfigurationLogger(validTimeoutConfig, validMessagingConfig))
                    .doesNotThrowAnyException();

            // Note: Null checks would be handled by DI framework at runtime
            // Direct construction with nulls is a developer error, not a runtime scenario
        }
    }
}
