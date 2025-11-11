package com.acme.reliable.processor;

import com.acme.reliable.repository.OutboxRepository;
import com.acme.reliable.spi.KafkaPublisher;
import com.acme.reliable.spi.MqPublisher;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
@Requires(beans = RedissonClient.class)
public class NotifyPublisher implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NotifyPublisher.class);
    private static final String NOTIFY_QUEUE = "outbox:notify";

    private final RedissonClient redisson;
    private final OutboxRepository outbox;
    private final MqPublisher mq;
    private final KafkaPublisher kafka;
    private final ScheduledExecutorService resub = Executors.newSingleThreadScheduledExecutor();
    private final Semaphore permits;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public NotifyPublisher(
            RedissonClient redisson, OutboxRepository outbox, MqPublisher mq, KafkaPublisher kafka) {
        this.redisson = redisson;
        this.outbox = outbox;
        this.mq = mq;
        this.kafka = kafka;
        this.permits = new Semaphore(32);
        subscribe();
        LOG.info("NotifyPublisher started with concurrency=32");
    }

    private void subscribe() {
        if (!running.get()) {
            return;
        }
        var q = redisson.getBlockingQueue(NOTIFY_QUEUE);
        q.takeAsync()
                .thenAcceptAsync(this::handleId)
                .whenComplete((ok, err) -> resub.execute(this::subscribe));
    }

    private void handleId(Object idObj) {
        if (!permits.tryAcquire()) {
            redisson.getQueue(NOTIFY_QUEUE).addAsync(idObj);
            return;
        }
        try {
            long id = Long.parseLong(String.valueOf(idObj));
            outbox
                    .claimIfNew(id, "NotifyPublisher")
                    .ifPresentOrElse(
                            row -> {
                                try {
                                    switch (row.getCategory()) {
                                        case "command", "reply" -> mq.publish(
                                                row.getTopic(), row.getKey(), row.getType(), row.getPayload(), row.getHeaders());
                                        case "event" -> kafka.publish(
                                                row.getTopic(), row.getKey(), row.getType(), row.getPayload(), row.getHeaders());
                                        default ->
                                                throw new IllegalArgumentException("bad category: " + row.getCategory());
                                    }
                                    outbox.markPublished(id);
                                    LOG.debug("Published outbox id={} category={}", id, row.getCategory());
                                } catch (Exception e) {
                                    LOG.warn("Failed to publish outbox id={}: {}", id, e.getMessage());
                                    outbox.markFailed(
                                            id, e.getMessage(), Instant.now().plusSeconds(backoff(row.getAttempts() + 1)));
                                } finally {
                                    permits.release();
                                }
                            },
                            () -> permits.release());
        } catch (Exception e) {
            LOG.error("Error handling notify id={}: {}", idObj, e.getMessage(), e);
            permits.release();
        }
    }

    private int backoff(int attempt) {
        return Math.min(300, 1 << Math.min(attempt, 8));
    }

    @Override
    @PreDestroy
    public void close() {
        LOG.info("Shutting down NotifyPublisher");
        running.set(false);
        resub.shutdownNow();
    }
}
