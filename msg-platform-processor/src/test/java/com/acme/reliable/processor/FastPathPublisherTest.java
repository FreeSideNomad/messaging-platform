package com.acme.reliable.processor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.support.TransactionSynchronization;
import java.sql.Connection;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Comprehensive unit tests for FastPathPublisher to achieve 80%+ coverage.
 *
 * <p>FastPathPublisher registers a transaction synchronization callback to publish
 * outbox messages immediately after transaction commit, providing fast-path delivery
 * via Redis notification queue.
 *
 * <p>Coverage areas:
 * - Successful registration and after-commit callback execution
 * - Transaction status present vs absent scenarios
 * - Exception handling during publish
 * - Multiple registrations
 * - Edge cases and boundary conditions
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FastPathPublisher Unit Tests")
class FastPathPublisherTest {

  @Mock private TransactionOperations<Connection> transactionOps;

  @Mock private OutboxRelay relay;

  @Mock private TransactionStatus<?> transactionStatus;

  private FastPathPublisher publisher;

  @BeforeEach
  void setUp() {
    publisher = new FastPathPublisher(transactionOps, relay);
  }

  @Test
  @DisplayName("Should register after-commit callback when transaction is present")
  void testRegisterAfterCommit_Success() {
    // Arrange
    long outboxId = 12345L;
    doReturn(Optional.of(transactionStatus)).when(transactionOps).findTransactionStatus();

    ArgumentCaptor<TransactionSynchronization> syncCaptor =
        ArgumentCaptor.forClass(TransactionSynchronization.class);

    // Act
    publisher.registerAfterCommit(outboxId);

    // Assert
    verify(transactionOps).findTransactionStatus();
    verify(transactionStatus).registerSynchronization(syncCaptor.capture());

    // Verify that no publish happened yet (only registration)
    verify(relay, never()).publishNow(anyLong());

    // Simulate transaction commit
    TransactionSynchronization sync = syncCaptor.getValue();
    sync.afterCommit();

    // Now verify publish was triggered
    verify(relay).publishNow(outboxId);
  }

  @Test
  @DisplayName("Should publish message after transaction commits successfully")
  void testAfterCommit_PublishesMessage() {
    // Arrange
    long outboxId = 99999L;
    doReturn(Optional.of(transactionStatus)).when(transactionOps).findTransactionStatus();

    ArgumentCaptor<TransactionSynchronization> syncCaptor =
        ArgumentCaptor.forClass(TransactionSynchronization.class);

    // Act
    publisher.registerAfterCommit(outboxId);
    verify(transactionStatus).registerSynchronization(syncCaptor.capture());

    TransactionSynchronization sync = syncCaptor.getValue();
    sync.afterCommit();

    // Assert
    verify(relay).publishNow(99999L);
  }

  @Test
  @DisplayName("Should register multiple callbacks for different outbox IDs")
  void testRegisterMultipleCallbacks() {
    // Arrange
    long outboxId1 = 100L;
    long outboxId2 = 200L;
    long outboxId3 = 300L;

    doReturn(Optional.of(transactionStatus)).when(transactionOps).findTransactionStatus();

    ArgumentCaptor<TransactionSynchronization> syncCaptor =
        ArgumentCaptor.forClass(TransactionSynchronization.class);

    // Act - Register three callbacks
    publisher.registerAfterCommit(outboxId1);
    publisher.registerAfterCommit(outboxId2);
    publisher.registerAfterCommit(outboxId3);

    // Assert - All three registrations happened
    verify(transactionStatus, times(3)).registerSynchronization(syncCaptor.capture());

    // Simulate commits and verify each ID is published
    for (int i = 0; i < 3; i++) {
      syncCaptor.getAllValues().get(i).afterCommit();
    }

    verify(relay).publishNow(100L);
    verify(relay).publishNow(200L);
    verify(relay).publishNow(300L);
  }

  @Test
  @DisplayName("Should do nothing when no transaction is present")
  void testRegisterAfterCommit_NoTransaction() {
    // Arrange
    long outboxId = 54321L;
    doReturn(Optional.empty()).when(transactionOps).findTransactionStatus();

    // Act
    publisher.registerAfterCommit(outboxId);

    // Assert
    verify(transactionOps).findTransactionStatus();
    verify(transactionStatus, never()).registerSynchronization(any());
    verify(relay, never()).publishNow(anyLong());
  }

  @Test
  @DisplayName("Should swallow exceptions from relay.publishNow during after-commit")
  void testAfterCommit_SwallowsPublishException() {
    // Arrange
    long outboxId = 888L;
    doReturn(Optional.of(transactionStatus)).when(transactionOps).findTransactionStatus();

    ArgumentCaptor<TransactionSynchronization> syncCaptor =
        ArgumentCaptor.forClass(TransactionSynchronization.class);

    doThrow(new RuntimeException("Redis connection failed"))
        .when(relay)
        .publishNow(outboxId);

    // Act
    publisher.registerAfterCommit(outboxId);
    verify(transactionStatus).registerSynchronization(syncCaptor.capture());

    TransactionSynchronization sync = syncCaptor.getValue();

    // Should not throw exception - exceptions are caught and ignored
    assertThatCode(() -> sync.afterCommit()).doesNotThrowAnyException();

    // Assert
    verify(relay).publishNow(888L);
  }

  @Test
  @DisplayName("Should handle outbox ID of zero")
  void testRegisterAfterCommit_ZeroId() {
    // Arrange
    long outboxId = 0L;
    doReturn(Optional.of(transactionStatus)).when(transactionOps).findTransactionStatus();

    ArgumentCaptor<TransactionSynchronization> syncCaptor =
        ArgumentCaptor.forClass(TransactionSynchronization.class);

    // Act
    publisher.registerAfterCommit(outboxId);
    verify(transactionStatus).registerSynchronization(syncCaptor.capture());
    syncCaptor.getValue().afterCommit();

    // Assert
    verify(relay).publishNow(0L);
  }

  @Test
  @DisplayName("Should verify constructor initializes fields correctly")
  void testConstructorInitialization() {
    // Act
    FastPathPublisher newPublisher = new FastPathPublisher(transactionOps, relay);

    // Assert - Verify it can be used
    doReturn(Optional.of(transactionStatus)).when(transactionOps).findTransactionStatus();

    assertThatCode(() -> newPublisher.registerAfterCommit(123L)).doesNotThrowAnyException();

    verify(transactionOps).findTransactionStatus();
  }
}
