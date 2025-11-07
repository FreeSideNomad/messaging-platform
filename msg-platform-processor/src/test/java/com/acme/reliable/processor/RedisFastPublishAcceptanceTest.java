package com.acme.reliable.processor;

import static org.junit.jupiter.api.Assertions.*;

import com.acme.reliable.persistence.jdbc.JdbcOutboxDao;
import com.acme.reliable.spi.KafkaPublisher;
import com.acme.reliable.spi.MqPublisher;
import com.acme.reliable.spi.OutboxRow;
import com.redis.testcontainers.RedisContainer;
import io.micronaut.context.ApplicationContext;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisFastPublishAcceptanceTest {
  private static final Logger LOG = LoggerFactory.getLogger(RedisFastPublishAcceptanceTest.class);

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
          .withDatabaseName("testdb")
          .withUsername("test")
          .withPassword("test");

  @Container
  static RedisContainer redis =
      new RedisContainer(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  private ApplicationContext context;
  private JdbcOutboxDao outboxDao;
  private MockMqPublisher mockMq;
  private MockKafkaPublisher mockKafka;
  private RedissonClient redisson;
  private NotifyPublisher notifyPublisher;
  private OutboxSweeper sweeper;

  @BeforeAll
  void setup() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("datasources.default.url", postgres.getJdbcUrl());
    properties.put("datasources.default.username", postgres.getUsername());
    properties.put("datasources.default.password", postgres.getPassword());
    properties.put("datasources.default.driver-class-name", "org.postgresql.Driver");
    properties.put("datasources.default.schema-generate", "NONE");
    properties.put("flyway.datasources.default.enabled", true);
    properties.put("flyway.datasources.default.locations", "classpath:db/migration-test");
    properties.put("redisson.enabled", true);
    properties.put(
        "redisson.address", "redis://" + redis.getHost() + ":" + redis.getFirstMappedPort());
    properties.put("micronaut.task.enabled", false); // Disable scheduled tasks in tests

    context = ApplicationContext.run(properties);

    outboxDao = context.getBean(JdbcOutboxDao.class);
    mockMq = new MockMqPublisher();
    mockKafka = new MockKafkaPublisher();

    Config config = new Config();
    config
        .useSingleServer()
        .setAddress("redis://" + redis.getHost() + ":" + redis.getFirstMappedPort());
    redisson = Redisson.create(config);

    notifyPublisher = new NotifyPublisher(redisson, outboxDao, mockMq, mockKafka);
    sweeper = new OutboxSweeper(outboxDao, mockMq, mockKafka);
  }

  @AfterAll
  void teardown() {
    if (notifyPublisher != null) {
      notifyPublisher.close();
    }
    if (redisson != null) {
      redisson.shutdown();
    }
    if (context != null) {
      context.close();
    }
  }

  @BeforeEach
  void clearData() {
    mockMq.clear();
    mockKafka.clear();
    redisson.getKeys().flushall();
  }

  @Test
  @DisplayName("Test 1: Notify happy path - 1k rows published with low latency")
  void testNotifyHappyPath() throws Exception {
    int count = 1000;
    List<Long> ids = new ArrayList<>();

    long startInsert = System.currentTimeMillis();
    for (int i = 0; i < count; i++) {
      long id =
          outboxDao.insertReturningId(
              new OutboxRow(
                  0,
                  "command",
                  "TEST.Q",
                  "key" + i,
                  "TestCommand",
                  "{\"data\":\"test" + i + "\"}",
                  Map.of(),
                  0));
      ids.add(id);
      redisson.getBlockingQueue("outbox:notify").add(String.valueOf(id));
    }
    long insertDuration = System.currentTimeMillis() - startInsert;
    LOG.info("Inserted {} rows in {}ms", count, insertDuration);

    long startPublish = System.currentTimeMillis();
    boolean allPublished =
        awaitCondition(() -> mockMq.getPublishedCount() == count, Duration.ofSeconds(30));
    long publishDuration = System.currentTimeMillis() - startPublish;

    assertTrue(allPublished, "All messages should be published within timeout");
    assertEquals(count, mockMq.getPublishedCount(), "MQ should receive all messages");
    LOG.info(
        "Published {} messages in {}ms (avg {}ms per message)",
        count,
        publishDuration,
        publishDuration / (double) count);

    for (long id : ids) {
      var row = outboxDao.claimIfNew(id);
      assertTrue(row.isEmpty(), "Message " + id + " should be claimed/published");
    }
  }

  @Test
  @DisplayName("Test 2: Notify loss - Sweeper drains NEW messages")
  void testNotifyLoss() throws Exception {
    int count = 100;
    List<Long> ids = new ArrayList<>();

    for (int i = 0; i < count; i++) {
      long id =
          outboxDao.insertReturningId(
              new OutboxRow(
                  0,
                  "event",
                  "test.topic",
                  "key" + i,
                  "TestEvent",
                  "{\"data\":\"test" + i + "\"}",
                  Map.of(),
                  0));
      ids.add(id);
    }

    LOG.info("Inserted {} rows WITHOUT Redis notify", count);

    sweeper.tick();
    sweeper.tick();

    boolean allPublished =
        awaitCondition(() -> mockKafka.getPublishedCount() == count, Duration.ofSeconds(10));
    assertTrue(allPublished, "Sweeper should drain all NEW messages");
    assertEquals(count, mockKafka.getPublishedCount(), "Kafka should receive all messages");
  }

  @Test
  @DisplayName("Test 3: Duplicate hints & multi-replica - only one publish per id")
  void testDuplicateHintsAndMultiReplica() throws Exception {
    int count = 50;
    List<Long> ids = new ArrayList<>();

    for (int i = 0; i < count; i++) {
      long id =
          outboxDao.insertReturningId(
              new OutboxRow(
                  0,
                  "command",
                  "TEST.Q",
                  "key" + i,
                  "TestCommand",
                  "{\"data\":\"test" + i + "\"}",
                  Map.of(),
                  0));
      ids.add(id);

      for (int j = 0; j < 5; j++) {
        redisson.getBlockingQueue("outbox:notify").add(String.valueOf(id));
      }
    }

    LOG.info("Inserted {} rows with 5x duplicate hints each", count);

    NotifyPublisher publisher2 = new NotifyPublisher(redisson, outboxDao, mockMq, mockKafka);
    NotifyPublisher publisher3 = new NotifyPublisher(redisson, outboxDao, mockMq, mockKafka);

    try {
      boolean allPublished =
          awaitCondition(() -> mockMq.getPublishedCount() >= count, Duration.ofSeconds(30));
      assertTrue(allPublished, "All messages should be published");
      assertEquals(
          count,
          mockMq.getPublishedCount(),
          "Should publish exactly once per message despite duplicates and multiple publishers");
    } finally {
      publisher2.close();
      publisher3.close();
    }
  }

  @Test
  @DisplayName("Test 4: Crash recovery - recoverStuck returns SENDING to NEW")
  void testCrashRecovery() throws Exception {
    long id =
        outboxDao.insertReturningId(
            new OutboxRow(
                0, "command", "TEST.Q", "key1", "TestCommand", "{\"data\":\"test\"}", Map.of(), 0));

    var claimed = outboxDao.claimIfNew(id);
    assertTrue(claimed.isPresent(), "Should claim message");

    Thread.sleep(11000);

    int recovered = outboxDao.recoverStuck(Duration.ofSeconds(10));
    assertEquals(1, recovered, "Should recover 1 stuck message");

    sweeper.tick();

    boolean published =
        awaitCondition(() -> mockMq.getPublishedCount() == 1, Duration.ofSeconds(5));
    assertTrue(published, "Recovered message should be published");
  }

  @Test
  @DisplayName("Test 5: Publish failures - backoff and retry")
  void testPublishFailures() throws Exception {
    mockMq.setFailureMode(3);

    long id =
        outboxDao.insertReturningId(
            new OutboxRow(
                0, "command", "TEST.Q", "key1", "TestCommand", "{\"data\":\"test\"}", Map.of(), 0));
    redisson.getList("outbox:notify").add(String.valueOf(id));

    Thread.sleep(2000);

    assertEquals(0, mockMq.getPublishedCount(), "Should not publish yet due to failures");

    for (int i = 0; i < 5; i++) {
      sweeper.tick();
      Thread.sleep(1000);
    }

    boolean published =
        awaitCondition(() -> mockMq.getPublishedCount() == 1, Duration.ofSeconds(10));
    assertTrue(published, "Should eventually publish after retries");
  }

  @Test
  @DisplayName("Test 6: Throughput smoke test - sustain target TPS")
  void testThroughputSmoke() throws Exception {
    int targetCount = 500;
    int targetTps = 100;

    ExecutorService executor = Executors.newFixedThreadPool(5);
    CountDownLatch latch = new CountDownLatch(targetCount);

    long startTime = System.currentTimeMillis();

    for (int i = 0; i < targetCount; i++) {
      final int index = i;
      executor.submit(
          () -> {
            try {
              long id =
                  outboxDao.insertReturningId(
                      new OutboxRow(
                          0,
                          "command",
                          "TEST.Q",
                          "key" + index,
                          "TestCommand",
                          "{\"data\":\"test" + index + "\"}",
                          Map.of(),
                          0));
              redisson.getBlockingQueue("outbox:notify").add(String.valueOf(id));
              latch.countDown();
            } catch (Exception e) {
              LOG.error("Error inserting message", e);
            }
          });
    }

    latch.await(30, TimeUnit.SECONDS);
    long insertDuration = System.currentTimeMillis() - startTime;

    boolean allPublished =
        awaitCondition(() -> mockMq.getPublishedCount() == targetCount, Duration.ofSeconds(60));
    long totalDuration = System.currentTimeMillis() - startTime;

    assertTrue(allPublished, "All messages should be published");
    assertEquals(targetCount, mockMq.getPublishedCount());

    double actualTps = (targetCount * 1000.0) / totalDuration;
    LOG.info(
        "Throughput: {} TPS (target: {} TPS), total time: {}ms",
        String.format("%.2f", actualTps),
        targetTps,
        totalDuration);

    assertTrue(actualTps >= targetTps * 0.8, "Should achieve at least 80% of target TPS");

    executor.shutdown();
  }

  private boolean awaitCondition(Callable<Boolean> condition, Duration timeout) throws Exception {
    long deadline = System.currentTimeMillis() + timeout.toMillis();
    while (System.currentTimeMillis() < deadline) {
      if (condition.call()) {
        return true;
      }
      Thread.sleep(100);
    }
    return condition.call();
  }

  static class MockMqPublisher implements MqPublisher {
    private final AtomicInteger publishCount = new AtomicInteger(0);
    private final Set<String> published = ConcurrentHashMap.newKeySet();
    private int failureMode = 0;
    private final AtomicInteger attemptCount = new AtomicInteger(0);

    @Override
    public void publish(
        String queue, String key, String type, String payload, Map<String, String> headers) {
      if (failureMode > 0 && attemptCount.incrementAndGet() <= failureMode) {
        throw new RuntimeException("Simulated MQ failure");
      }
      String uniqueKey = queue + ":" + key;
      if (published.add(uniqueKey)) {
        publishCount.incrementAndGet();
      }
    }

    public int getPublishedCount() {
      return publishCount.get();
    }

    public void clear() {
      publishCount.set(0);
      published.clear();
      attemptCount.set(0);
      failureMode = 0;
    }

    public void setFailureMode(int failures) {
      this.failureMode = failures;
    }
  }

  static class MockKafkaPublisher implements KafkaPublisher {
    private final AtomicInteger publishCount = new AtomicInteger(0);
    private final Set<String> published = ConcurrentHashMap.newKeySet();

    @Override
    public void publish(
        String topic, String key, String type, String payload, Map<String, String> headers) {
      String uniqueKey = topic + ":" + key;
      if (published.add(uniqueKey)) {
        publishCount.incrementAndGet();
      }
    }

    public int getPublishedCount() {
      return publishCount.get();
    }

    public void clear() {
      publishCount.set(0);
      published.clear();
    }
  }
}
