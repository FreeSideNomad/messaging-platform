package com.acme.reliable.config;

import java.time.Duration;

/**
 * Configuration for various timeout, retry, and performance settings. Pure POJO - no framework
 * dependencies.
 */
public class TimeoutConfig {

  private Duration commandLease = Duration.ofMinutes(5);
  private Duration maxBackoff = Duration.ofMinutes(5);
  private Duration syncWait = Duration.ZERO; // Async by default (no blocking wait)
  private Duration outboxSweepInterval = Duration.ofSeconds(1); // Fast sweep for high throughput
  private int outboxBatchSize = 2000; // 4x increased from 500 for high throughput
  private Duration outboxClaimTimeout =
      Duration.ofSeconds(1); // Timeout for reclaiming stuck CLAIMED messages

  public Duration getCommandLease() {
    return commandLease;
  }

  public void setCommandLease(Duration commandLease) {
    this.commandLease = commandLease;
  }

  public long getCommandLeaseSeconds() {
    return commandLease.toSeconds();
  }

  public Duration getMaxBackoff() {
    return maxBackoff;
  }

  public void setMaxBackoff(Duration maxBackoff) {
    this.maxBackoff = maxBackoff;
  }

  public long getMaxBackoffMillis() {
    return maxBackoff.toMillis();
  }

  public Duration getSyncWait() {
    return syncWait;
  }

  public void setSyncWait(Duration syncWait) {
    this.syncWait = syncWait;
  }

  public long getSyncWaitMillis() {
    return syncWait.toMillis();
  }

  public boolean isAsync() {
    return syncWait.isZero();
  }

  public int getOutboxBatchSize() {
    return outboxBatchSize;
  }

  public void setOutboxBatchSize(int outboxBatchSize) {
    this.outboxBatchSize = outboxBatchSize;
  }

  public Duration getOutboxSweepInterval() {
    return outboxSweepInterval;
  }

  public void setOutboxSweepInterval(Duration outboxSweepInterval) {
    this.outboxSweepInterval = outboxSweepInterval;
  }

  public String getOutboxSweepIntervalString() {
    return outboxSweepInterval.toMillis() + "ms";
  }

  public Duration getOutboxClaimTimeout() {
    return outboxClaimTimeout;
  }

  public void setOutboxClaimTimeout(Duration outboxClaimTimeout) {
    this.outboxClaimTimeout = outboxClaimTimeout;
  }

  public long getOutboxClaimTimeoutSeconds() {
    return outboxClaimTimeout.toSeconds();
  }
}
