package com.acme.reliable.processor;

import io.micronaut.context.annotation.Requires;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.support.TransactionSynchronization;
import jakarta.inject.Singleton;
import java.sql.Connection;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Requires(beans = RedissonClient.class)
public class RedisNotifier {
  private static final Logger LOG = LoggerFactory.getLogger(RedisNotifier.class);
  private static final String NOTIFY_QUEUE = "outbox:notify";

  private final TransactionOperations<Connection> transactionOps;
  private final RedissonClient redisson;

  public RedisNotifier(TransactionOperations<Connection> transactionOps, RedissonClient redisson) {
    this.transactionOps = transactionOps;
    this.redisson = redisson;
  }

  public void registerAfterCommit(long outboxId) {
    transactionOps
        .findTransactionStatus()
        .ifPresent(
            status -> {
              status.registerSynchronization(
                  new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                      try {
                        redisson.getList(NOTIFY_QUEUE).add(String.valueOf(outboxId));
                        LOG.debug("Notified Redis for outbox id={}", outboxId);
                      } catch (Exception e) {
                        LOG.warn(
                            "Failed to notify Redis for outbox id={}: {}",
                            outboxId,
                            e.getMessage());
                      }
                    }
                  });
            });
  }
}
