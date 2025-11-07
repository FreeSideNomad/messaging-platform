package com.acme.reliable.spi;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OutboxDao {

  Optional<OutboxRow> claimIfNew(long id);

  List<OutboxRow> sweepBatch(int max);

  void markPublished(long id);

  void markFailed(long id, String error, Instant nextAttempt);

  int recoverStuck(Duration olderThan);
}
