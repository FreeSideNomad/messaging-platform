package com.acme.reliable.processor.services;

import com.acme.reliable.domain.Outbox;
import com.acme.reliable.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("OutboxServiceImpl Tests")
class OutboxServiceImplTest {

    private OutboxServiceImpl outboxService;
    private OutboxRepository mockRepository;

    @BeforeEach
    void setup() {
        mockRepository = mock(OutboxRepository.class);
        outboxService = new OutboxServiceImpl(mockRepository);
    }

    @Nested
    @DisplayName("addReturningId Tests")
    class AddReturningIdTests {

        @Test
        @DisplayName("should serialize headers to JSON when adding outbox")
        void testAddReturningId_SerializesHeaders() {
            // Arrange
            Map<String, String> headers = Map.of(
                    "commandId", "cmd-123",
                    "timestamp", "2025-11-09T00:00:00Z"
            );
            Outbox outbox = new Outbox(
                    null, "command", "TOPIC", "key", "TestCommand",
                    "{}", headers, "PENDING", 0
            );
            when(mockRepository.insertReturningId(any(), any(), any(), any(), any(), any()))
                    .thenReturn(42L);

            // Act
            long result = outboxService.addReturningId(outbox);

            // Assert
            assertThat(result).isEqualTo(42L);
            verify(mockRepository).insertReturningId(
                    eq("command"),
                    eq("TOPIC"),
                    eq("key"),
                    eq("TestCommand"),
                    eq("{}"),
                    argThat(json -> json.contains("commandId") && json.contains("timestamp"))
            );
        }

        @Test
        @DisplayName("should handle empty headers gracefully")
        void testAddReturningId_EmptyHeaders() {
            Outbox outbox = new Outbox(
                    null, "command", "TOPIC", "key", "TestCommand",
                    "{}", new HashMap<>(), "PENDING", 0
            );
            when(mockRepository.insertReturningId(any(), any(), any(), any(), any(), any()))
                    .thenReturn(43L);

            long result = outboxService.addReturningId(outbox);

            assertThat(result).isEqualTo(43L);
            verify(mockRepository).insertReturningId(
                    any(), any(), any(), any(), any(), eq("{}")
            );
        }

        @Test
        @DisplayName("should handle large payloads")
        void testAddReturningId_LargePayload() {
            String largePayload = "{\"data\":\"" + "x".repeat(5000) + "\"}";
            Outbox outbox = new Outbox(
                    null, "command", "TOPIC", "key", "TestCommand",
                    largePayload, new HashMap<>(), "PENDING", 0
            );
            when(mockRepository.insertReturningId(any(), any(), any(), any(), any(), any()))
                    .thenReturn(44L);

            long result = outboxService.addReturningId(outbox);

            assertThat(result).isEqualTo(44L);
            verify(mockRepository).insertReturningId(
                    any(), any(), any(), any(), eq(largePayload), any()
            );
        }
    }

    @Nested
    @DisplayName("claimOne Tests")
    class ClaimOneTests {

        @Test
        @DisplayName("should return from repository when entry exists")
        void testClaimOne_ReturnsFromRepository() {
            long id = 1L;
            Outbox mockOutbox = new Outbox(
                    id, "command", "TOPIC", "key", "TestCommand", "{}", new HashMap<>(), "CLAIMED", 0
            );
            when(mockRepository.claimOne(eq(id), anyString())).thenReturn(Optional.of(mockOutbox));

            Optional<Outbox> result = outboxService.claimOne(id, "TEST_CLAIMER");

            assertThat(result).isPresent().contains(mockOutbox);
            verify(mockRepository).claimOne(eq(id), anyString());
        }

        @Test
        @DisplayName("should return empty when no entry available")
        void testClaimOne_ReturnsEmpty() {
            long id = 999L;
            when(mockRepository.claimOne(eq(id), anyString())).thenReturn(Optional.empty());

            Optional<Outbox> result = outboxService.claimOne(id, "TEST_CLAIMER");

            assertThat(result).isEmpty();
            verify(mockRepository).claimOne(eq(id), anyString());
        }
    }

    @Nested
    @DisplayName("claim Tests")
    class ClaimTests {

        @Test
        @DisplayName("should return batch of messages")
        void testClaim_ReturnsBatch() {
            int batchSize = 10;
            String claimer = "TestClaimer";
            List<Outbox> mockMessages = List.of(
                    new Outbox(1L, "command", "T1", "k1", "C1", "{}", new HashMap<>(), "NEW", 0),
                    new Outbox(2L, "command", "T2", "k2", "C2", "{}", new HashMap<>(), "NEW", 0),
                    new Outbox(3L, "command", "T3", "k3", "C3", "{}", new HashMap<>(), "NEW", 0)
            );

            when(mockRepository.claim(batchSize, claimer)).thenReturn(mockMessages);

            List<Outbox> result = outboxService.claim(batchSize, claimer);

            assertThat(result).isEqualTo(mockMessages);
            verify(mockRepository).claim(batchSize, claimer);
        }
    }

    @Nested
    @DisplayName("markPublished Tests")
    class MarkPublishedTests {

        @Test
        @DisplayName("should delegate to repository")
        void testMarkPublished_CallsRepository() {
            long outboxId = 1L;

            outboxService.markPublished(outboxId);

            verify(mockRepository).markPublished(outboxId);
        }
    }

    @Nested
    @DisplayName("reschedule Tests")
    class RescheduleTests {

        @Test
        @DisplayName("should reschedule with backoff")
        void testReschedule_CallsRepository() {
            long outboxId = 1L;
            long backoffMs = 5000;
            String error = "Network timeout";

            outboxService.reschedule(outboxId, backoffMs, error);

            verify(mockRepository).reschedule(outboxId, backoffMs, error);
        }
    }
}
