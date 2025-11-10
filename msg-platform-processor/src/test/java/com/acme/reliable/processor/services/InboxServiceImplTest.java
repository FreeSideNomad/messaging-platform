package com.acme.reliable.processor.services;

import com.acme.reliable.repository.InboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("InboxServiceImpl Tests")
class InboxServiceImplTest {

    private InboxServiceImpl inboxService;
    private InboxRepository mockRepository;

    @BeforeEach
    void setup() {
        mockRepository = mock(InboxRepository.class);
        inboxService = new InboxServiceImpl(mockRepository);
    }

    @Nested
    @DisplayName("markIfAbsent Tests")
    class MarkIfAbsentTests {

        @Test
        @DisplayName("should return true when message is first time processed")
        void testMarkIfAbsent_NewMessage_ReturnsTrue() {
            String messageId = "msg-123";
            String handler = "OrderCommandHandler";

            when(mockRepository.insertIfAbsent(messageId, handler)).thenReturn(1);

            boolean result = inboxService.markIfAbsent(messageId, handler);

            assertThat(result).isTrue();
            verify(mockRepository).insertIfAbsent(messageId, handler);
        }

        @Test
        @DisplayName("should return false when message was already processed")
        void testMarkIfAbsent_DuplicateMessage_ReturnsFalse() {
            String messageId = "msg-456";
            String handler = "PaymentCommandHandler";

            when(mockRepository.insertIfAbsent(messageId, handler)).thenReturn(0);

            boolean result = inboxService.markIfAbsent(messageId, handler);

            assertThat(result).isFalse();
            verify(mockRepository).insertIfAbsent(messageId, handler);
        }

        @Test
        @DisplayName("should handle large payload in idempotency check")
        void testMarkIfAbsent_LargePayload_Handles() {
            String messageId = "msg-large";
            String handler = "DocumentHandler";

            when(mockRepository.insertIfAbsent(messageId, handler)).thenReturn(1);

            boolean result = inboxService.markIfAbsent(messageId, handler);

            assertThat(result).isTrue();
            verify(mockRepository).insertIfAbsent(eq(messageId), eq(handler));
        }

        @Test
        @DisplayName("should handle empty message ID")
        void testMarkIfAbsent_EmptyMessageId_Handles() {
            String messageId = "";
            String handler = "EmptyHandler";

            when(mockRepository.insertIfAbsent(messageId, handler)).thenReturn(1);

            boolean result = inboxService.markIfAbsent(messageId, handler);

            assertThat(result).isTrue();
            verify(mockRepository).insertIfAbsent(messageId, handler);
        }

        @Test
        @DisplayName("should use messageId and handler for duplicate detection")
        void testMarkIfAbsent_IdempotencyKey_DetectsDuplicates() {
            String messageId = "transaction-123";
            String handler = "TransactionHandler";

            when(mockRepository.insertIfAbsent(messageId, handler)).thenReturn(1);
            boolean result1 = inboxService.markIfAbsent(messageId, handler);

            when(mockRepository.insertIfAbsent(messageId, handler)).thenReturn(0);
            boolean result2 = inboxService.markIfAbsent(messageId, handler);

            assertThat(result1).isTrue();
            assertThat(result2).isFalse();
            verify(mockRepository, times(2)).insertIfAbsent(messageId, handler);
        }
    }
}
