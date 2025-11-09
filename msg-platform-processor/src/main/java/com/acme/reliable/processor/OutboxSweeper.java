package com.acme.reliable.processor;

import com.acme.reliable.repository.OutboxRepository;
import com.acme.reliable.spi.KafkaPublisher;
import com.acme.reliable.spi.MqPublisher;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class OutboxSweeper {
  private static final Logger LOG = LoggerFactory.getLogger(OutboxSweeper.class);

  private final OutboxRepository outbox;
  private final MqPublisher mq;
  private final KafkaPublisher kafka;

  public OutboxSweeper(OutboxRepository outbox, MqPublisher mq, KafkaPublisher kafka) {
    this.outbox = outbox;
    this.mq = mq;
    this.kafka = kafka;
  }

  @Scheduled(fixedDelay = "PT1S")
  public void tick() {
    try {
      int recovered = outbox.recoverStuck(Duration.ofSeconds(10));
      if (recovered > 0) {
        LOG.info("Recovered {} stuck SENDING messages", recovered);
      }

      var rows = outbox.sweepBatch(500);
      if (!rows.isEmpty()) {
        LOG.debug("Sweeping {} outbox messages", rows.size());
      }

      for (var row : rows) {
        try {
          switch (row.getCategory()) {
            case "command", "reply" ->
                mq.publish(row.getTopic(), row.getKey(), row.getType(), row.getPayload(), row.getHeaders());
            case "event" ->
                kafka.publish(row.getTopic(), row.getKey(), row.getType(), row.getPayload(), row.getHeaders());
            default -> throw new IllegalArgumentException("Unknown category: " + row.getCategory());
          }
          outbox.markPublished(row.getId());
          LOG.debug("Swept and published outbox id={} category={}", row.getId(), row.getCategory());
        } catch (Exception e) {
          LOG.warn("Failed to publish outbox id={}: {}", row.getId(), e.getMessage());
          outbox.markFailed(
              row.getId(), e.getMessage(), Instant.now().plusSeconds(backoff(row.getAttempts() + 1)));
        }
      }
    } catch (Exception e) {
      LOG.error("Error in OutboxSweeper tick: {}", e.getMessage(), e);
    }
  }

  private int backoff(int attempt) {
    return Math.min(300, 1 << Math.min(attempt, 8));
  }
}
