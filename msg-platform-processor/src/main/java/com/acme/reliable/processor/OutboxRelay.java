package com.acme.reliable.processor;

import com.acme.reliable.config.TimeoutConfig;
import com.acme.reliable.service.OutboxService;
import com.acme.reliable.spi.CommandQueue;
import com.acme.reliable.spi.EventPublisher;
import com.acme.reliable.domain.Outbox;
import io.micronaut.scheduling.annotation.Scheduled;
import io.micronaut.transaction.TransactionOperations;
import jakarta.inject.Singleton;
import java.sql.Connection;
import java.util.List;

@Singleton
public class OutboxRelay {
  private final OutboxService store;
  private final CommandQueue mq;
  private final EventPublisher kafka;
  private final long maxBackoffMillis;
  private final int batchSize;
  private final TransactionOperations<Connection> transactionOps;

  public OutboxRelay(
      OutboxService s,
      CommandQueue m,
      EventPublisher k,
      TimeoutConfig timeoutConfig,
      TransactionOperations<Connection> transactionOps) {
    this.store = s;
    this.mq = m;
    this.kafka = k;
    this.maxBackoffMillis = timeoutConfig.getMaxBackoffMillis();
    this.batchSize = timeoutConfig.getOutboxBatchSize();
    this.transactionOps = transactionOps;
  }

  public void publishNow(long id) {
    transactionOps.executeWrite(
        status -> {
          store.claimOne(id).ifPresent(this::sendAndMark);
          return null;
        });
  }

  @Scheduled(fixedDelay = "${timeout.outbox-sweep-interval:1s}")
  void sweepOnce() {
    // Claim messages atomically, then release the transaction before slow I/O
    List<Outbox> rows =
        transactionOps.executeWrite(
            status -> {
              return store.claim(batchSize, host());
            });
    // Process claimed messages outside transaction to avoid holding connections during slow I/O
    rows.forEach(this::sendAndMark);
  }

  private void sendAndMark(Outbox r) {
    try {
      // Publish to external system (MQ/Kafka) - this is slow I/O, done outside any transaction
      switch (r.getCategory()) {
        case "command", "reply" -> mq.send(r.getTopic(), r.getPayload(), r.getHeaders());
        case "event" -> kafka.publish(r.getTopic(), r.getKey(), r.getPayload(), r.getHeaders());
        default -> throw new IllegalArgumentException("Unknown category " + r.getCategory());
      }
      // Mark published in a quick transaction after successful publish
      transactionOps.executeWrite(
          status -> {
            store.markPublished(r.getId());
            return null;
          });
    } catch (Exception e) {
      // Reschedule in a quick transaction - if this fails, message stays CLAIMED
      // and will be picked up again on next sweep when it's no longer claimed
      long backoff =
          Math.min(maxBackoffMillis, (long) Math.pow(2, Math.max(1, r.getAttempts() + 1)) * 1000L);
      transactionOps.executeWrite(
          status -> {
            store.reschedule(r.getId(), backoff, e.toString());
            return null;
          });
    }
  }

  private String host() {
    try {
      return java.net.InetAddress.getLoopbackAddress().getHostName();
    } catch (Exception e) {
      return "unknown-host";
    }
  }
}
