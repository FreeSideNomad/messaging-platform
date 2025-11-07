package com.acme.reliable.processor;

import com.acme.reliable.spi.KafkaPublisher;
import com.acme.reliable.spi.MqPublisher;
import com.acme.reliable.spi.OutboxDao;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class OutboxSweeper {
  private static final Logger LOG = LoggerFactory.getLogger(OutboxSweeper.class);

  private final OutboxDao outbox;
  private final MqPublisher mq;
  private final KafkaPublisher kafka;

  public OutboxSweeper(OutboxDao outbox, MqPublisher mq, KafkaPublisher kafka) {
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
          switch (row.category()) {
            case "command", "reply" ->
                mq.publish(row.topic(), row.key(), row.type(), row.payload(), row.headers());
            case "event" ->
                kafka.publish(row.topic(), row.key(), row.type(), row.payload(), row.headers());
            default -> throw new IllegalArgumentException("Unknown category: " + row.category());
          }
          outbox.markPublished(row.id());
          LOG.debug("Swept and published outbox id={} category={}", row.id(), row.category());
        } catch (Exception e) {
          LOG.warn("Failed to publish outbox id={}: {}", row.id(), e.getMessage());
          outbox.markFailed(
              row.id(), e.getMessage(), Instant.now().plusSeconds(backoff(row.attempts() + 1)));
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
