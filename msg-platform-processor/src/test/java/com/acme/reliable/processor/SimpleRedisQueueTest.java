package com.acme.reliable.processor;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.*;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Simple test to verify Redis Queue data type compatibility between test code
 * (getList().add()) and NotifyPublisher (getBlockingQueue())
 */
@Testcontainers
class SimpleRedisQueueTest {

    @Container
    static GenericContainer<?> redis =
            new RedisContainer(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private RedissonClient redisson;

    @BeforeEach
    void setup() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + redis.getHost() + ":" + redis.getFirstMappedPort());
        redisson = Redisson.create(config);
        redisson.getKeys().flushall();
    }

    @AfterEach
    void teardown() {
        if (redisson != null) {
            redisson.shutdown();
        }
    }

    @Test
    @DisplayName("Verify Queue read/write compatibility: List.add() and BlockingQueue.take()")
    void testQueueCompatibility() throws Exception {
        String queueName = "test:queue";

        // Producer: Simulate what test code does - add to List
        var list = redisson.getList(queueName);
        list.add("message1");
        list.add("message2");
        list.add("message3");

        // Consumer: Simulate what NotifyPublisher does - take from BlockingQueue
        var queue = redisson.getBlockingQueue(queueName);

        // These should all work because Queue and List share the same underlying Redis List
        Object msg1 = queue.poll(2, TimeUnit.SECONDS);
        assertEquals("message1", msg1, "First message should be message1");

        Object msg2 = queue.poll(2, TimeUnit.SECONDS);
        assertEquals("message2", msg2, "Second message should be message2");

        Object msg3 = queue.poll(2, TimeUnit.SECONDS);
        assertEquals("message3", msg3, "Third message should be message3");

        Object empty = queue.poll(100, TimeUnit.MILLISECONDS);
        assertNull(empty, "Queue should be empty after consuming all messages");
    }

    @Test
    @Disabled("Flaky test - async operations don't guarantee ordering with CompletableFuture.allOf(). Redis may reorder items despite waiting for async ops to complete. Needs proper fix with Redisson transactions or different test approach.")
    @DisplayName("Verify Queue.add() and BlockingQueue.take() async")
    void testQueueAsyncCompatibility() throws Exception {
        String queueName = "test:queue:async";

        // This is what test code does: add messages to a List
        var list = redisson.getList(queueName);
        var add1 = list.addAsync("async1").toCompletableFuture();
        var add2 = list.addAsync("async2").toCompletableFuture();

        // Wait for both async operations to complete before reading
        CompletableFuture.allOf(add1, add2).get(5, TimeUnit.SECONDS);

        // This is what NotifyPublisher does: take from BlockingQueue
        var queue = redisson.getBlockingQueue(queueName);

        CompletableFuture<Object> future = queue.takeAsync().toCompletableFuture();
        Object result = future.get(5, TimeUnit.SECONDS);

        assertEquals("async1", result, "Should receive async1 from queue");

        // Second message
        CompletableFuture<Object> future2 = queue.takeAsync().toCompletableFuture();
        Object result2 = future2.get(5, TimeUnit.SECONDS);

        assertEquals("async2", result2, "Should receive async2 from queue");
    }

    @Test
    @DisplayName("Verify re-queue logic: Queue.add() after failed processing")
    void testRequeueLogic() throws Exception {
        String queueName = "test:requeue";

        // Initial message
        var list = redisson.getList(queueName);
        list.add("initial");

        // Consumer takes message
        var queue = redisson.getBlockingQueue(queueName);
        Object msg = queue.poll(1, TimeUnit.SECONDS);
        assertEquals("initial", msg);

        // Simulate failure: put message back using Queue.add()
        var requeueQueue = redisson.getQueue(queueName);
        requeueQueue.add("initial");

        // Should be able to re-consume it
        Object retried = queue.poll(1, TimeUnit.SECONDS);
        assertEquals("initial", retried, "Message should be available for retry");
    }
}
