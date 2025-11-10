package com.acme.reliable.processor.services;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.acme.reliable.repository.CommandRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CommandServiceImpl Tests")
class CommandServiceImplTest {

  private CommandServiceImpl commandService;
  private CommandRepository mockRepository;

  @BeforeEach
  void setup() {
    mockRepository = mock(CommandRepository.class);
    commandService = new CommandServiceImpl(mockRepository);
  }

  @Nested
  @DisplayName("savePending Tests")
  class SavePendingTests {

    @Test
    @DisplayName("should save pending command and return UUID")
    void testSavePending_ReturnsUuid() {
      String name = "CreateUser";
      String idem = "key-123";
      String businessKey = "user-123";
      String payload = "{\"email\":\"user@example.com\"}";
      String replyJson = "{}";

      UUID result = commandService.savePending(name, idem, businessKey, payload, replyJson);

      assertThat(result).isNotNull();
      verify(mockRepository).insertPending(
          any(UUID.class), eq(name), eq(businessKey), eq(payload), eq(idem), eq(replyJson)
      );
    }
  }

  @Nested
  @DisplayName("find Tests")
  class FindTests {

    @Test
    @DisplayName("should find command by ID")
    void testFind_ReturnsRecord() {
      UUID commandId = UUID.randomUUID();
      com.acme.reliable.domain.Command mockCommand = mock(com.acme.reliable.domain.Command.class);
      when(mockRepository.findById(commandId)).thenReturn(Optional.of(mockCommand));

      var result = commandService.find(commandId);

      assertThat(result).isPresent();
      verify(mockRepository).findById(commandId);
    }

    @Test
    @DisplayName("should return empty when command not found")
    void testFind_ReturnsEmpty() {
      UUID commandId = UUID.randomUUID();
      when(mockRepository.findById(commandId)).thenReturn(Optional.empty());

      var result = commandService.find(commandId);

      assertThat(result).isEmpty();
      verify(mockRepository).findById(commandId);
    }
  }

  @Nested
  @DisplayName("markRunning Tests")
  class MarkRunningTests {

    @Test
    @DisplayName("should mark command as running")
    void testMarkRunning_UpdatesStatus() {
      UUID commandId = UUID.randomUUID();
      Instant now = Instant.now();

      commandService.markRunning(commandId, now);

      verify(mockRepository).updateToRunning(eq(commandId), any(Timestamp.class));
    }
  }

  @Nested
  @DisplayName("markSucceeded Tests")
  class MarkSucceededTests {

    @Test
    @DisplayName("should mark command as succeeded")
    void testMarkSucceeded_UpdatesStatus() {
      UUID commandId = UUID.randomUUID();

      commandService.markSucceeded(commandId);

      verify(mockRepository).updateToSucceeded(commandId);
    }
  }

  @Nested
  @DisplayName("markFailed Tests")
  class MarkFailedTests {

    @Test
    @DisplayName("should mark command as failed with error")
    void testMarkFailed_UpdatesStatus() {
      UUID commandId = UUID.randomUUID();
      String error = "Database connection timeout";

      commandService.markFailed(commandId, error);

      verify(mockRepository).updateToFailed(commandId, error);
    }
  }

  @Nested
  @DisplayName("bumpRetry Tests")
  class BumpRetryTests {

    @Test
    @DisplayName("should bump retry count")
    void testBumpRetry_UpdatesCount() {
      UUID commandId = UUID.randomUUID();
      String error = "Temporary failure";

      commandService.bumpRetry(commandId, error);

      verify(mockRepository).incrementRetries(commandId, error);
    }
  }

  @Nested
  @DisplayName("markTimedOut Tests")
  class MarkTimedOutTests {

    @Test
    @DisplayName("should mark command as timed out")
    void testMarkTimedOut_UpdatesStatus() {
      UUID commandId = UUID.randomUUID();
      String reason = "Execution timeout after 5 minutes";

      commandService.markTimedOut(commandId, reason);

      verify(mockRepository).updateToTimedOut(commandId, reason);
    }
  }

  @Nested
  @DisplayName("existsByIdempotencyKey Tests")
  class ExistsByIdempotencyKeyTests {

    @Test
    @DisplayName("should return true if command with idempotency key exists")
    void testExistsByIdempotencyKey_Found() {
      String idempotencyKey = "key-456";
      when(mockRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(true);

      boolean exists = commandService.existsByIdempotencyKey(idempotencyKey);

      assertThat(exists).isTrue();
      verify(mockRepository).existsByIdempotencyKey(idempotencyKey);
    }

    @Test
    @DisplayName("should return false if command with idempotency key not found")
    void testExistsByIdempotencyKey_NotFound() {
      String idempotencyKey = "key-789";
      when(mockRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false);

      boolean exists = commandService.existsByIdempotencyKey(idempotencyKey);

      assertThat(exists).isFalse();
      verify(mockRepository).existsByIdempotencyKey(idempotencyKey);
    }
  }
}
