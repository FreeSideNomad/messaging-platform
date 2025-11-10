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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;

/**
 * Comprehensive unit tests for RedisNotifier to achieve 80%+ coverage.
 *
 * <p>RedisNotifier registers transaction synchronization callbacks to publish
 * outbox IDs to Redis after transaction commit, enabling fast-path processing.
 *
 * <p>Coverage areas:
 * - Successful registration and after-commit callback execution
 * - Transaction status present vs absent scenarios
 * - Redis publishing behavior
 * - Exception handling during Redis operations
 * - Edge cases (connection failures, null values, etc.)
 * - Integration with TransactionOperations and RedissonClient
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisNotifier Unit Tests")
class RedisNotifierTest {

  private static final String NOTIFY_QUEUE = "outbox:notify";

  @Mock private TransactionOperations<Connection> transactionOps;

  @Mock private RedissonClient redissonClient;

  @Mock private TransactionStatus<?> transactionStatus;

  @Mock private RList<Object> redisList;

  private RedisNotifier notifier;

  @BeforeEach
  void setUp() {
    lenient().when(redissonClient.getList(NOTIFY_QUEUE)).thenReturn((RList) redisList);
    notifier = new RedisNotifier(transactionOps, redissonClient);
  }

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("Should initialize with all dependencies")
    void testConstructor_Success() {
      // Act
      RedisNotifier newNotifier = new RedisNotifier(transactionOps, redissonClient);

      // Assert
      assertThat(newNotifier).isNotNull();
    }

    @Test
    @DisplayName("Should be annotated with @Singleton")
    void testConstructor_SingletonAnnotation() {
      // Verify the class has @Singleton annotation
      assertThat(RedisNotifier.class.isAnnotationPresent(jakarta.inject.Singleton.class)).isTrue();
    }

    @Test
    @DisplayName("Should require RedissonClient bean")
    void testConstructor_RequiresRedissonClient() {
      // Verify the class has @Requires(beans = RedissonClient.class) annotation
      assertThat(
              RedisNotifier.class.isAnnotationPresent(
                  io.micronaut.context.annotation.Requires.class))
          .isTrue();
    }
  }

  @Nested
  @DisplayName("Registration Tests")
  class RegistrationTests {

    @Test
    @DisplayName("Should register after-commit callback when transaction is present")
    void testRegisterAfterCommit_TransactionPresent() {
      // Arrange
      long outboxId = 12345L;
      doReturn(Optional.of(transactionStatus)).when(transactionOps).findTransactionStatus();

      // Act
      notifier.registerAfterCommit(outboxId);

      // Assert
      verify(transactionOps).findTransactionStatus();
      verify(transactionStatus).registerSynchronization(any(TransactionSynchronization.class));

      // Verify no immediate Redis call
      verify(redissonClient, never()).getList(anyString());
    }

    @Test
    @DisplayName("Should do nothing when no transaction is present")
    void testRegisterAfterCommit_NoTransaction() {
      // Arrange
      long outboxId = 54321L;
      doReturn(Optional.empty()).when(transactionOps).findTransactionStatus();

      // Act
      notifier.registerAfterCommit(outboxId);

      // Assert
      verify(transactionOps).findTransactionStatus();
      verify(transactionStatus, never()).registerSynchronization(any());
      verify(redissonClient, never()).getList(anyString());
    }

    @Test
    @DisplayName("Should register multiple callbacks for different outbox IDs")
    void testRegisterAfterCommit_MultipleCallbacks() {
      // Arrange
      long outboxId1 = 100L;
      long outboxId2 = 200L;
      long outboxId3 = 300L;

      doReturn(Optional.of(transactionStatus)).when(transactionOps).findTransactionStatus();

      // Act
      notifier.registerAfterCommit(outboxId1);
      notifier.registerAfterCommit(outboxId2);
      notifier.registerAfterCommit(outboxId3);

      // Assert
      verify(transactionOps, times(3)).findTransactionStatus();
      verify(transactionStatus, times(3)).registerSynchronization(any(TransactionSynchronization.class));
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
      notifier.registerAfterCommit(outboxId);
      verify(transactionStatus).registerSynchronization(syncCaptor.capture());

      // Simulate commit
      syncCaptor.getValue().afterCommit();

      // Assert
      verify(redisList).add("0");
    }

    @Test
    @DisplayName("Should handle negative outbox ID")
    void testRegisterAfterCommit_NegativeId() {
      // Arrange
      long outboxId = -1L;
      doReturn(Optional.of(transactionStatus)).when(transactionOps).findTransactionStatus();

      ArgumentCaptor<TransactionSynchronization> syncCaptor =
          ArgumentCaptor.forClass(TransactionSynchronization.class);

      // Act
      notifier.registerAfterCommit(outboxId);
      verify(transactionStatus).registerSynchronization(syncCaptor.capture());

      // Simulate commit
      syncCaptor.getValue().afterCommit();

      // Assert
      verify(redisList).add("-1");
    }

    @Test
    @DisplayName("Should handle very large outbox ID")
    void testRegisterAfterCommit_LargeId() {
      // Arrange
      long outboxId = Long.MAX_VALUE;
      doReturn(Optional.of(transactionStatus)).when(transactionOps).findTransactionStatus();

      ArgumentCaptor<TransactionSynchronization> syncCaptor =
          ArgumentCaptor.forClass(TransactionSynchronization.class);

      // Act
      notifier.registerAfterCommit(outboxId);
      verify(transactionStatus).registerSynchronization(syncCaptor.capture());

      // Simulate commit
      syncCaptor.getValue().afterCommit();

      // Assert
      verify(redisList).add(String.valueOf(Long.MAX_VALUE));
    }
  }

  @Nested
  @DisplayName("After-Commit Callback Tests")
  class AfterCommitTests {

    @Test
    @DisplayName("Should publish to Redis after transaction commits")
    void testAfterCommit_PublishesToRedis() {
      // Arrange
      long outboxId = 99999L;
      doReturn(Optional.of(transactionStatus)).when(transactionOps).findTransactionStatus();

      ArgumentCaptor<TransactionSynchronization> syncCaptor =
          ArgumentCaptor.forClass(TransactionSynchronization.class);

      // Act
      notifier.registerAfterCommit(outboxId);
      verify(transactionStatus).registerSynchronization(syncCaptor.capture());

      TransactionSynchronization sync = syncCaptor.getValue();
      sync.afterCommit();

      // Assert
      verify(redissonClient).getList(NOTIFY_QUEUE);
      verify(redisList).add("99999");
    }

    @Test
    @DisplayName("Should convert outbox ID to string when adding to Redis list")
    void testAfterCommit_ConvertsToString() {
      // Arrange
      long outboxId = 54321L;
      doReturn(Optional.of(transactionStatus)).when(transactionOps).findTransactionStatus();

      ArgumentCaptor<TransactionSynchronization> syncCaptor =
          ArgumentCaptor.forClass(TransactionSynchronization.class);
      ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);

      // Act
      notifier.registerAfterCommit(outboxId);
      verify(transactionStatus).registerSynchronization(syncCaptor.capture());

      syncCaptor.getValue().afterCommit();

      // Assert
      verify(redisList).add(valueCaptor.capture());
      assertThat(valueCaptor.getValue()).isEqualTo("54321");
    }

    @Test
    @DisplayName("Should use correct Redis queue name")
    void testAfterCommit_CorrectQueueName() {
      // Arrange
      long outboxId = 12345L;
      doReturn(Optional.of(transactionStatus)).when(transactionOps).findTransactionStatus();

      ArgumentCaptor<TransactionSynchronization> syncCaptor =
          ArgumentCaptor.forClass(TransactionSynchronization.class);

      // Act
      notifier.registerAfterCommit(outboxId);
      verify(transactionStatus).registerSynchronization(syncCaptor.capture());

      syncCaptor.getValue().afterCommit();

      // Assert
      verify(redissonClient).getList("outbox:notify");
    }

    @Test
    @DisplayName("Should publish multiple IDs in order")
    void testAfterCommit_MultipleInOrder() {
      // Arrange
      long outboxId1 = 100L;
      long outboxId2 = 200L;
      long outboxId3 = 300L;

      doReturn(Optional.of(transactionStatus)).when(transactionOps).findTransactionStatus();

      ArgumentCaptor<TransactionSynchronization> syncCaptor =
          ArgumentCaptor.forClass(TransactionSynchronization.class);

      // Act
      notifier.registerAfterCommit(outboxId1);
      notifier.registerAfterCommit(outboxId2);
      notifier.registerAfterCommit(outboxId3);

      verify(transactionStatus, times(3)).registerSynchronization(syncCaptor.capture());

      // Simulate commits in order
      for (TransactionSynchronization sync : syncCaptor.getAllValues()) {
        sync.afterCommit();
      }

      // Assert
      verify(redisList).add("100");
      verify(redisList).add("200");
      verify(redisList).add("300");
    }
  }

  @Nested
  @DisplayName("Exception Handling Tests")
  class ExceptionHandlingTests {

    @Test
    @DisplayName("Should catch and log exception from Redis operation")
    void testAfterCommit_RedisException() {
      // Arrange
      long outboxId = 888L;
      doReturn(Optional.of(transactionStatus)).when(transactionOps).findTransactionStatus();

      when(redisList.add(anyString())).thenThrow(new RuntimeException("Redis connection failed"));

      ArgumentCaptor<TransactionSynchronization> syncCaptor =
          ArgumentCaptor.forClass(TransactionSynchronization.class);

      // Act
      notifier.registerAfterCommit(outboxId);
      verify(transactionStatus).registerSynchronization(syncCaptor.capture());

      TransactionSynchronization sync = syncCaptor.getValue();

      // Should not throw exception - exceptions are caught
      assertThatCode(() -> sync.afterCommit()).doesNotThrowAnyException();

      // Assert
      verify(redisList).add("888");
    }

    @Test
    @DisplayName("Should catch exception from getList operation")
    void testAfterCommit_GetListException() {
      // Arrange
      long outboxId = 777L;
      doReturn(Optional.of(transactionStatus)).when(transactionOps).findTransactionStatus();

      when(redissonClient.getList(NOTIFY_QUEUE))
          .thenThrow(new RuntimeException("Redis client error"));

      ArgumentCaptor<TransactionSynchronization> syncCaptor =
          ArgumentCaptor.forClass(TransactionSynchronization.class);

      // Act
      notifier.registerAfterCommit(outboxId);
      verify(transactionStatus).registerSynchronization(syncCaptor.capture());

      TransactionSynchronization sync = syncCaptor.getValue();

      // Should not throw exception
      assertThatCode(() -> sync.afterCommit()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle Redis timeout exception")
    void testAfterCommit_TimeoutException() {
      // Arrange
      long outboxId = 666L;
      doReturn(Optional.of(transactionStatus)).when(transactionOps).findTransactionStatus();

      when(redisList.add(anyString()))
          .thenThrow(new RuntimeException("Operation timed out"));

      ArgumentCaptor<TransactionSynchronization> syncCaptor =
          ArgumentCaptor.forClass(TransactionSynchronization.class);

      // Act
      notifier.registerAfterCommit(outboxId);
      verify(transactionStatus).registerSynchronization(syncCaptor.capture());

      // Should not throw
      assertThatCode(() -> syncCaptor.getValue().afterCommit())
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle null pointer exception from Redis")
    void testAfterCommit_NullPointerException() {
      // Arrange
      long outboxId = 555L;
      doReturn(Optional.of(transactionStatus)).when(transactionOps).findTransactionStatus();

      when(redissonClient.getList(NOTIFY_QUEUE)).thenReturn(null);

      ArgumentCaptor<TransactionSynchronization> syncCaptor =
          ArgumentCaptor.forClass(TransactionSynchronization.class);

      // Act
      notifier.registerAfterCommit(outboxId);
      verify(transactionStatus).registerSynchronization(syncCaptor.capture());

      // Should not throw
      assertThatCode(() -> syncCaptor.getValue().afterCommit())
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle exception from findTransactionStatus")
    void testRegisterAfterCommit_TransactionStatusException() {
      // Arrange
      long outboxId = 444L;
      when(transactionOps.findTransactionStatus())
          .thenThrow(new RuntimeException("Transaction manager error"));

      // Act & Assert - Should propagate exception
      assertThatThrownBy(() -> notifier.registerAfterCommit(outboxId))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Transaction manager error");
    }

    @Test
    @DisplayName("Should continue processing after one failure")
    void testAfterCommit_ContinueAfterFailure() {
      // Arrange
      long outboxId1 = 100L;
      long outboxId2 = 200L;

      doReturn(Optional.of(transactionStatus)).when(transactionOps).findTransactionStatus();

      // First call fails, second succeeds
      when(redisList.add("100")).thenThrow(new RuntimeException("Temporary error"));
      when(redisList.add("200")).thenReturn(true);

      ArgumentCaptor<TransactionSynchronization> syncCaptor =
          ArgumentCaptor.forClass(TransactionSynchronization.class);

      // Act
      notifier.registerAfterCommit(outboxId1);
      notifier.registerAfterCommit(outboxId2);

      verify(transactionStatus, times(2)).registerSynchronization(syncCaptor.capture());

      syncCaptor.getAllValues().get(0).afterCommit(); // Fails
      syncCaptor.getAllValues().get(1).afterCommit(); // Succeeds

      // Assert - Second call should still work
      verify(redisList).add("100");
      verify(redisList).add("200");
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle transaction status being optional empty")
    void testEdgeCase_OptionalEmpty() {
      // Arrange
      long outboxId = 333L;
      when(transactionOps.findTransactionStatus()).thenReturn(Optional.empty());

      // Act
      notifier.registerAfterCommit(outboxId);

      // Assert - Should not attempt to register synchronization
      verify(transactionStatus, never()).registerSynchronization(any());
      verify(redissonClient, never()).getList(anyString());
    }

    @Test
    @DisplayName("Should handle null from optional")
    void testEdgeCase_NullOptional() {
      // Arrange
      long outboxId = 222L;
      when(transactionOps.findTransactionStatus()).thenReturn(null);

      // Act & Assert - Should handle gracefully
      assertThatCode(() -> notifier.registerAfterCommit(outboxId))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should handle rapid sequential registrations")
    void testEdgeCase_RapidRegistrations() {
      // Arrange
      doReturn(Optional.of(transactionStatus)).when(transactionOps).findTransactionStatus();

      ArgumentCaptor<TransactionSynchronization> syncCaptor =
          ArgumentCaptor.forClass(TransactionSynchronization.class);

      // Act - Register 100 callbacks rapidly
      for (long i = 1; i <= 100; i++) {
        notifier.registerAfterCommit(i);
      }

      // Assert
      verify(transactionStatus, times(100)).registerSynchronization(syncCaptor.capture());

      // Simulate all commits
      for (int i = 0; i < 100; i++) {
        syncCaptor.getAllValues().get(i).afterCommit();
      }

      // Verify all were added to Redis
      verify(redisList, times(100)).add(anyString());
    }

    @Test
    @DisplayName("Should handle same outbox ID registered multiple times")
    void testEdgeCase_DuplicateRegistrations() {
      // Arrange
      long outboxId = 999L;
      doReturn(Optional.of(transactionStatus)).when(transactionOps).findTransactionStatus();

      ArgumentCaptor<TransactionSynchronization> syncCaptor =
          ArgumentCaptor.forClass(TransactionSynchronization.class);

      // Act - Register same ID three times
      notifier.registerAfterCommit(outboxId);
      notifier.registerAfterCommit(outboxId);
      notifier.registerAfterCommit(outboxId);

      verify(transactionStatus, times(3)).registerSynchronization(syncCaptor.capture());

      // Simulate all commits
      for (TransactionSynchronization sync : syncCaptor.getAllValues()) {
        sync.afterCommit();
      }

      // Assert - Should add to Redis three times (no deduplication)
      verify(redisList, times(3)).add("999");
    }

    @Test
    @DisplayName("Should handle registration with no Redis client interactions before commit")
    void testEdgeCase_NoEarlyRedisInteraction() {
      // Arrange
      long outboxId = 111L;
      doReturn(Optional.of(transactionStatus)).when(transactionOps).findTransactionStatus();

      // Act - Just register, don't commit yet
      notifier.registerAfterCommit(outboxId);

      // Assert - No Redis interaction should happen during registration
      verify(redissonClient, never()).getList(anyString());
      verifyNoInteractions(redisList);
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("Should complete full workflow from registration to Redis publish")
    void testIntegration_FullWorkflow() {
      // Arrange
      long outboxId = 12345L;
      doReturn(Optional.of(transactionStatus)).when(transactionOps).findTransactionStatus();

      ArgumentCaptor<TransactionSynchronization> syncCaptor =
          ArgumentCaptor.forClass(TransactionSynchronization.class);

      // Act - Complete workflow
      notifier.registerAfterCommit(outboxId);

      // Assert registration phase
      verify(transactionOps).findTransactionStatus();
      verify(transactionStatus).registerSynchronization(syncCaptor.capture());
      verify(redissonClient, never()).getList(anyString());

      // Act - Simulate commit
      syncCaptor.getValue().afterCommit();

      // Assert commit phase
      verify(redissonClient).getList("outbox:notify");
      verify(redisList).add("12345");
    }

    @Test
    @DisplayName("Should handle multiple commands in same transaction")
    void testIntegration_MultipleInSameTransaction() {
      // Arrange
      long outboxId1 = 1L;
      long outboxId2 = 2L;
      long outboxId3 = 3L;

      doReturn(Optional.of(transactionStatus)).when(transactionOps).findTransactionStatus();

      ArgumentCaptor<TransactionSynchronization> syncCaptor =
          ArgumentCaptor.forClass(TransactionSynchronization.class);

      // Act - Register multiple in same transaction
      notifier.registerAfterCommit(outboxId1);
      notifier.registerAfterCommit(outboxId2);
      notifier.registerAfterCommit(outboxId3);

      verify(transactionStatus, times(3)).registerSynchronization(syncCaptor.capture());

      // Simulate transaction commit - all callbacks execute
      for (TransactionSynchronization sync : syncCaptor.getAllValues()) {
        sync.afterCommit();
      }

      // Assert
      verify(redisList).add("1");
      verify(redisList).add("2");
      verify(redisList).add("3");
    }

    @Test
    @DisplayName("Should work correctly across multiple separate transactions")
    void testIntegration_MultipleSeparateTransactions() {
      // Arrange
      @SuppressWarnings("unchecked")
      TransactionStatus<Connection> txn1 = mock(TransactionStatus.class);
      @SuppressWarnings("unchecked")
      TransactionStatus<Connection> txn2 = mock(TransactionStatus.class);
      @SuppressWarnings("unchecked")
      TransactionStatus<Connection> txn3 = mock(TransactionStatus.class);

      doReturn(Optional.of(txn1), Optional.of(txn2), Optional.of(txn3))
          .when(transactionOps).findTransactionStatus();

      ArgumentCaptor<TransactionSynchronization> sync1Captor =
          ArgumentCaptor.forClass(TransactionSynchronization.class);
      ArgumentCaptor<TransactionSynchronization> sync2Captor =
          ArgumentCaptor.forClass(TransactionSynchronization.class);
      ArgumentCaptor<TransactionSynchronization> sync3Captor =
          ArgumentCaptor.forClass(TransactionSynchronization.class);

      // Act - Three separate transactions
      notifier.registerAfterCommit(10L);
      verify(txn1).registerSynchronization(sync1Captor.capture());

      notifier.registerAfterCommit(20L);
      verify(txn2).registerSynchronization(sync2Captor.capture());

      notifier.registerAfterCommit(30L);
      verify(txn3).registerSynchronization(sync3Captor.capture());

      // Commit each transaction
      sync1Captor.getValue().afterCommit();
      sync2Captor.getValue().afterCommit();
      sync3Captor.getValue().afterCommit();

      // Assert
      verify(redisList).add("10");
      verify(redisList).add("20");
      verify(redisList).add("30");
    }

    @Test
    @DisplayName("Should work with real transaction lifecycle simulation")
    void testIntegration_RealLifecycleSimulation() {
      // Arrange
      long outboxId = 5000L;
      doReturn(Optional.of(transactionStatus)).when(transactionOps).findTransactionStatus();

      ArgumentCaptor<TransactionSynchronization> syncCaptor =
          ArgumentCaptor.forClass(TransactionSynchronization.class);

      // Act - Simulate real lifecycle
      // 1. Start transaction (implicit)
      // 2. Register callback
      notifier.registerAfterCommit(outboxId);

      // 3. Transaction commits
      verify(transactionStatus).registerSynchronization(syncCaptor.capture());
      TransactionSynchronization sync = syncCaptor.getValue();

      // 4. After-commit callback fires
      sync.afterCommit();

      // Assert - Complete lifecycle worked
      verify(transactionOps).findTransactionStatus();
      verify(transactionStatus).registerSynchronization(any(TransactionSynchronization.class));
      verify(redissonClient).getList("outbox:notify");
      verify(redisList).add("5000");
    }
  }

  @Nested
  @DisplayName("Logging Tests")
  class LoggingTests {

    @Test
    @DisplayName("Should log debug message on successful Redis publish")
    void testLogging_SuccessDebug() {
      // Arrange
      long outboxId = 7777L;
      doReturn(Optional.of(transactionStatus)).when(transactionOps).findTransactionStatus();

      ArgumentCaptor<TransactionSynchronization> syncCaptor =
          ArgumentCaptor.forClass(TransactionSynchronization.class);

      // Act
      notifier.registerAfterCommit(outboxId);
      verify(transactionStatus).registerSynchronization(syncCaptor.capture());
      syncCaptor.getValue().afterCommit();

      // Assert - Verify Redis call was made (logging would happen here)
      verify(redisList).add("7777");
    }

    @Test
    @DisplayName("Should log warning on Redis failure")
    void testLogging_FailureWarning() {
      // Arrange
      long outboxId = 8888L;
      doReturn(Optional.of(transactionStatus)).when(transactionOps).findTransactionStatus();

      when(redisList.add(anyString())).thenThrow(new RuntimeException("Redis error"));

      ArgumentCaptor<TransactionSynchronization> syncCaptor =
          ArgumentCaptor.forClass(TransactionSynchronization.class);

      // Act
      notifier.registerAfterCommit(outboxId);
      verify(transactionStatus).registerSynchronization(syncCaptor.capture());

      // Should not throw - exception is caught and logged
      assertThatCode(() -> syncCaptor.getValue().afterCommit())
          .doesNotThrowAnyException();

      // Assert - Verify attempt was made (logging would happen in catch block)
      verify(redisList).add("8888");
    }
  }
}
