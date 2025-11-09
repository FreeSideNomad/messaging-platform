package com.acme.reliable.repository;

import com.acme.reliable.domain.Command;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

/** Repository for Command persistence and state management */
public interface CommandRepository {

  /** Insert a new command in PENDING status */
  void insertPending(
      UUID id,
      String name,
      String businessKey,
      String payload,
      String idempotencyKey,
      String reply);

  /** Retrieve a command by ID */
  Optional<Command> findById(UUID id);

  /** Update command to RUNNING status with processing lease */
  void updateToRunning(UUID id, Timestamp lease);

  /** Mark command as SUCCEEDED */
  void updateToSucceeded(UUID id);

  /** Mark command as FAILED with error message */
  void updateToFailed(UUID id, String error);

  /** Increment retry count and record error */
  void incrementRetries(UUID id, String error);

  /** Mark command as TIMED_OUT */
  void updateToTimedOut(UUID id, String reason);

  /** Check if a command with given idempotency key already exists */
  boolean existsByIdempotencyKey(String key);
}
