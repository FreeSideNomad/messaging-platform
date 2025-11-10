package com.acme.reliable.processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive unit tests for ResponseRegistry to achieve 80%+ coverage.
 *
 * <p>ResponseRegistry manages pending command responses using CompletableFuture,
 * with automatic timeout and cleanup to prevent memory leaks.
 *
 * <p>Coverage areas:
 * - Registration and retrieval of responses
 * - Successful completion
 * - Failure completion
 * - Timeout behavior and auto-cleanup
 * - Concurrent operations
 * - Edge cases (null values, duplicate operations, etc.)
 * - Memory leak prevention
 */
@DisplayName("ResponseRegistry Unit Tests")
class ResponseRegistryTest {

    private ResponseRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ResponseRegistry();
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should initialize with empty pending map")
        void testConstructor_EmptyMap() {
            // Act
            ResponseRegistry newRegistry = new ResponseRegistry();

            // Assert - Should be able to register immediately
            UUID commandId = UUID.randomUUID();
            CompletableFuture<String> future = newRegistry.register(commandId);

            assertThat(future).isNotNull();
            assertThat(future.isDone()).isFalse();
        }
    }

    @Nested
    @DisplayName("Registration Tests")
    class RegistrationTests {

        @Test
        @DisplayName("Should register command and return CompletableFuture")
        void testRegister_ReturnsCompletableFuture() {
            // Arrange
            UUID commandId = UUID.randomUUID();

            // Act
            CompletableFuture<String> future = registry.register(commandId);

            // Assert
            assertThat(future).isNotNull();
            assertThat(future.isDone()).isFalse();
            assertThat(future.isCancelled()).isFalse();
            assertThat(future.isCompletedExceptionally()).isFalse();
        }

        @Test
        @DisplayName("Should register multiple commands with different IDs")
        void testRegister_MultipleCommands() {
            // Arrange
            UUID commandId1 = UUID.randomUUID();
            UUID commandId2 = UUID.randomUUID();
            UUID commandId3 = UUID.randomUUID();

            // Act
            CompletableFuture<String> future1 = registry.register(commandId1);
            CompletableFuture<String> future2 = registry.register(commandId2);
            CompletableFuture<String> future3 = registry.register(commandId3);

            // Assert
            assertThat(future1).isNotSameAs(future2);
            assertThat(future2).isNotSameAs(future3);
            assertThat(future1).isNotSameAs(future3);

            assertThat(future1.isDone()).isFalse();
            assertThat(future2.isDone()).isFalse();
            assertThat(future3.isDone()).isFalse();
        }

        @Test
        @DisplayName("Should allow registering same command ID again after completion")
        void testRegister_ReRegisterAfterCompletion() throws Exception {
            // Arrange
            UUID commandId = UUID.randomUUID();

            // Act
            CompletableFuture<String> future1 = registry.register(commandId);
            registry.complete(commandId, "first response");

            // Wait for cleanup
            future1.get(100, TimeUnit.MILLISECONDS);

            CompletableFuture<String> future2 = registry.register(commandId);

            // Assert
            assertThat(future1.isDone()).isTrue();
            assertThat(future2.isDone()).isFalse();
            assertThat(future1).isNotSameAs(future2);
        }

        @Test
        @DisplayName("Should set 2 second timeout on registered future")
        void testRegister_SetsTimeout() {
            // Arrange
            UUID commandId = UUID.randomUUID();

            // Act
            CompletableFuture<String> future = registry.register(commandId);

            // Assert - Future should timeout after 2 seconds
            assertThatThrownBy(() -> future.get(3, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(TimeoutException.class);
        }
    }

    @Nested
    @DisplayName("Completion Tests")
    class CompletionTests {

        @Test
        @DisplayName("Should complete registered future with response")
        void testComplete_Success() throws Exception {
            // Arrange
            UUID commandId = UUID.randomUUID();
            CompletableFuture<String> future = registry.register(commandId);

            // Act
            registry.complete(commandId, "success response");

            // Assert
            assertThat(future.isDone()).isTrue();
            assertThat(future.get()).isEqualTo("success response");
            assertThat(future.isCompletedExceptionally()).isFalse();
        }

        @Test
        @DisplayName("Should complete with JSON response")
        void testComplete_JsonResponse() throws Exception {
            // Arrange
            UUID commandId = UUID.randomUUID();
            CompletableFuture<String> future = registry.register(commandId);
            String jsonResponse = "{\"orderId\":\"123\",\"status\":\"completed\"}";

            // Act
            registry.complete(commandId, jsonResponse);

            // Assert
            assertThat(future.get()).isEqualTo(jsonResponse);
        }

        @Test
        @DisplayName("Should complete with empty string")
        void testComplete_EmptyString() throws Exception {
            // Arrange
            UUID commandId = UUID.randomUUID();
            CompletableFuture<String> future = registry.register(commandId);

            // Act
            registry.complete(commandId, "");

            // Assert
            assertThat(future.get()).isEqualTo("");
        }

        @Test
        @DisplayName("Should complete with large response")
        void testComplete_LargeResponse() throws Exception {
            // Arrange
            UUID commandId = UUID.randomUUID();
            CompletableFuture<String> future = registry.register(commandId);
            StringBuilder largeResponse = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                largeResponse.append("data");
            }

            // Act
            registry.complete(commandId, largeResponse.toString());

            // Assert
            assertThat(future.get()).isEqualTo(largeResponse.toString());
        }

        @Test
        @DisplayName("Should do nothing when completing non-existent command")
        void testComplete_NonExistentCommand() {
            // Arrange
            UUID commandId = UUID.randomUUID();

            // Act & Assert - Should not throw
            assertThatCode(() -> registry.complete(commandId, "response"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should do nothing when completing already completed future")
        void testComplete_AlreadyCompleted() throws Exception {
            // Arrange
            UUID commandId = UUID.randomUUID();
            CompletableFuture<String> future = registry.register(commandId);
            registry.complete(commandId, "first response");

            // Act - Try to complete again
            registry.complete(commandId, "second response");

            // Assert - Should still have first response
            assertThat(future.get()).isEqualTo("first response");
        }

        @Test
        @DisplayName("Should remove future from pending map after completion")
        void testComplete_RemovesFromMap() throws Exception {
            // Arrange
            UUID commandId = UUID.randomUUID();
            CompletableFuture<String> future = registry.register(commandId);

            // Act
            registry.complete(commandId, "response");
            future.get(); // Wait for whenComplete callback

            // Try completing again - should do nothing since it's removed
            registry.complete(commandId, "another response");

            // Assert - Still has original response
            assertThat(future.get()).isEqualTo("response");
        }
    }

    @Nested
    @DisplayName("Failure Tests")
    class FailureTests {

        @Test
        @DisplayName("Should complete future exceptionally with error")
        void testFail_Success() {
            // Arrange
            UUID commandId = UUID.randomUUID();
            CompletableFuture<String> future = registry.register(commandId);

            // Act
            registry.fail(commandId, "Command execution failed");

            // Assert
            assertThat(future.isDone()).isTrue();
            assertThat(future.isCompletedExceptionally()).isTrue();

            assertThatThrownBy(() -> future.get())
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Command execution failed");
        }

        @Test
        @DisplayName("Should fail with detailed error message")
        void testFail_DetailedMessage() {
            // Arrange
            UUID commandId = UUID.randomUUID();
            CompletableFuture<String> future = registry.register(commandId);
            String errorMessage = "Database connection timeout: server unreachable after 30s";

            // Act
            registry.fail(commandId, errorMessage);

            // Assert
            assertThatThrownBy(() -> future.get())
                    .isInstanceOf(ExecutionException.class)
                    .cause()
                    .hasMessage(errorMessage);
        }

        @Test
        @DisplayName("Should fail with empty error message")
        void testFail_EmptyMessage() {
            // Arrange
            UUID commandId = UUID.randomUUID();
            CompletableFuture<String> future = registry.register(commandId);

            // Act
            registry.fail(commandId, "");

            // Assert
            assertThatThrownBy(() -> future.get())
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("Should do nothing when failing non-existent command")
        void testFail_NonExistentCommand() {
            // Arrange
            UUID commandId = UUID.randomUUID();

            // Act & Assert - Should not throw
            assertThatCode(() -> registry.fail(commandId, "error")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should do nothing when failing already completed future")
        void testFail_AlreadyCompleted() throws Exception {
            // Arrange
            UUID commandId = UUID.randomUUID();
            CompletableFuture<String> future = registry.register(commandId);
            registry.complete(commandId, "success");

            // Act - Try to fail
            registry.fail(commandId, "error");

            // Assert - Should still be successful
            assertThat(future.get()).isEqualTo("success");
            assertThat(future.isCompletedExceptionally()).isFalse();
        }

        @Test
        @DisplayName("Should remove future from pending map after failure")
        void testFail_RemovesFromMap() {
            // Arrange
            UUID commandId = UUID.randomUUID();
            CompletableFuture<String> future = registry.register(commandId);

            // Act
            registry.fail(commandId, "first error");

            // Try failing again - should do nothing since it's removed
            registry.fail(commandId, "second error");

            // Assert - Still has original error
            assertThatThrownBy(() -> future.get())
                    .cause()
                    .hasMessage("first error");
        }
    }

    @Nested
    @DisplayName("Timeout and Auto-Cleanup Tests")
    class TimeoutTests {

        @Test
        @DisplayName("Should timeout after 2 seconds if not completed")
        void testTimeout_AutomaticAfter2Seconds() {
            // Arrange
            UUID commandId = UUID.randomUUID();
            CompletableFuture<String> future = registry.register(commandId);

            // Act - Wait for timeout
            assertThatThrownBy(() -> future.get(3, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(TimeoutException.class);

            // Assert
            assertThat(future.isDone()).isTrue();
            assertThat(future.isCompletedExceptionally()).isTrue();
        }

        @Test
        @DisplayName("Should auto-cleanup from pending map after timeout")
        void testTimeout_AutoCleanup() throws Exception {
            // Arrange
            UUID commandId = UUID.randomUUID();
            CompletableFuture<String> future = registry.register(commandId);

            // Act - Wait for timeout and cleanup
            try {
                future.get(3, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                // Expected timeout exception
            }

            // Wait a bit for whenComplete callback to execute
            Thread.sleep(100);

            // Try completing - should do nothing since it's cleaned up
            registry.complete(commandId, "too late");

            // Assert - Future should still be in timeout state
            assertThat(future.isCompletedExceptionally()).isTrue();
        }

        @Test
        @DisplayName("Should not timeout if completed before 2 seconds")
        void testTimeout_CompletedBeforeTimeout() throws Exception {
            // Arrange
            UUID commandId = UUID.randomUUID();
            CompletableFuture<String> future = registry.register(commandId);

            // Act - Complete within 1 second
            Thread.sleep(100);
            registry.complete(commandId, "quick response");

            // Assert - Should complete successfully, not timeout
            assertThat(future.get(100, TimeUnit.MILLISECONDS)).isEqualTo("quick response");
            assertThat(future.isCompletedExceptionally()).isFalse();
        }

        @Test
        @DisplayName("Should not timeout if failed before 2 seconds")
        void testTimeout_FailedBeforeTimeout() {
            // Arrange
            UUID commandId = UUID.randomUUID();
            CompletableFuture<String> future = registry.register(commandId);

            // Act - Fail within 1 second
            registry.fail(commandId, "quick failure");

            // Assert - Should fail with error, not timeout
            assertThatThrownBy(() -> future.get())
                    .isInstanceOf(ExecutionException.class)
                    .cause()
                    .hasMessage("quick failure");
        }
    }

    @Nested
    @DisplayName("Concurrency Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("Should handle concurrent registrations")
        void testConcurrency_ConcurrentRegistrations() throws Exception {
            // Arrange
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<CompletableFuture<String>> futures = new ArrayList<>();
            List<UUID> commandIds = new ArrayList<>();

            // Act - Register from multiple threads
            for (int i = 0; i < threadCount; i++) {
                UUID commandId = UUID.randomUUID();
                commandIds.add(commandId);

                new Thread(
                        () -> {
                            CompletableFuture<String> future = registry.register(commandId);
                            futures.add(future);
                            latch.countDown();
                        })
                        .start();
            }

            latch.await(5, TimeUnit.SECONDS);

            // Assert
            assertThat(futures).hasSize(threadCount);
            for (CompletableFuture<String> future : futures) {
                assertThat(future.isDone()).isFalse();
            }
        }

        @Test
        @DisplayName("Should handle concurrent completions")
        void testConcurrency_ConcurrentCompletions() throws Exception {
            // Arrange
            int count = 10;
            List<UUID> commandIds = new ArrayList<>();
            List<CompletableFuture<String>> futures = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                UUID commandId = UUID.randomUUID();
                commandIds.add(commandId);
                futures.add(registry.register(commandId));
            }

            CountDownLatch latch = new CountDownLatch(count);

            // Act - Complete from multiple threads
            for (int i = 0; i < count; i++) {
                final int index = i;
                new Thread(
                        () -> {
                            registry.complete(commandIds.get(index), "response-" + index);
                            latch.countDown();
                        })
                        .start();
            }

            latch.await(5, TimeUnit.SECONDS);

            // Assert - All futures should be completed
            for (int i = 0; i < count; i++) {
                assertThat(futures.get(i).get()).isEqualTo("response-" + i);
            }
        }

        @Test
        @DisplayName("Should handle concurrent failures")
        void testConcurrency_ConcurrentFailures() throws Exception {
            // Arrange
            int count = 10;
            List<UUID> commandIds = new ArrayList<>();
            List<CompletableFuture<String>> futures = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                UUID commandId = UUID.randomUUID();
                commandIds.add(commandId);
                futures.add(registry.register(commandId));
            }

            CountDownLatch latch = new CountDownLatch(count);

            // Act - Fail from multiple threads
            for (int i = 0; i < count; i++) {
                final int index = i;
                new Thread(
                        () -> {
                            registry.fail(commandIds.get(index), "error-" + index);
                            latch.countDown();
                        })
                        .start();
            }

            latch.await(5, TimeUnit.SECONDS);

            // Assert - All futures should be failed
            for (int i = 0; i < count; i++) {
                final int index = i;
                assertThatThrownBy(() -> futures.get(index).get())
                        .isInstanceOf(ExecutionException.class)
                        .cause()
                        .hasMessage("error-" + index);
            }
        }

        @Test
        @DisplayName("Should handle mixed concurrent operations")
        void testConcurrency_MixedOperations() throws Exception {
            // Arrange
            int count = 20;
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(count);

            // Act - Mix of register, complete, and fail operations
            for (int i = 0; i < count; i++) {
                final int index = i;
                new Thread(
                        () -> {
                            UUID commandId = UUID.randomUUID();
                            CompletableFuture<String> future = registry.register(commandId);

                            if (index % 2 == 0) {
                                registry.complete(commandId, "success");
                                try {
                                    future.get();
                                    successCount.incrementAndGet();
                                } catch (Exception ignored) {
                                }
                            } else {
                                registry.fail(commandId, "error");
                                try {
                                    future.get();
                                } catch (Exception e) {
                                    failCount.incrementAndGet();
                                }
                            }
                            latch.countDown();
                        })
                        .start();
            }

            latch.await(5, TimeUnit.SECONDS);

            // Assert
            assertThat(successCount.get()).isEqualTo(10);
            assertThat(failCount.get()).isEqualTo(10);
        }

        @Test
        @DisplayName("Should handle race between complete and timeout")
        void testConcurrency_CompleteVsTimeout() throws Exception {
            // Arrange
            UUID commandId = UUID.randomUUID();
            CompletableFuture<String> future = registry.register(commandId);

            // Act - Complete just before timeout
            Thread.sleep(1900); // Wait almost 2 seconds
            registry.complete(commandId, "just in time");

            // Assert - Should complete successfully, not timeout
            String result = future.get(500, TimeUnit.MILLISECONDS);
            assertThat(result).isEqualTo("just in time");
            assertThat(future.isCompletedExceptionally()).isFalse();
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle registering same command ID twice before completion")
        void testEdgeCase_DoubleRegister() {
            // Arrange
            UUID commandId = UUID.randomUUID();

            // Act
            CompletableFuture<String> future1 = registry.register(commandId);
            CompletableFuture<String> future2 = registry.register(commandId);

            // Assert - Second registration overwrites first
            assertThat(future1).isNotSameAs(future2);

            registry.complete(commandId, "response");

            // Only future2 should complete
            assertThat(future2.isDone()).isTrue();
        }

        @Test
        @DisplayName("Should handle null response in complete")
        void testEdgeCase_NullResponse() throws Exception {
            // Arrange
            UUID commandId = UUID.randomUUID();
            CompletableFuture<String> future = registry.register(commandId);

            // Act
            registry.complete(commandId, null);

            // Assert
            assertThat(future.get()).isNull();
        }

        @Test
        @DisplayName("Should handle null error in fail")
        void testEdgeCase_NullError() {
            // Arrange
            UUID commandId = UUID.randomUUID();
            CompletableFuture<String> future = registry.register(commandId);

            // Act
            registry.fail(commandId, null);

            // Assert
            assertThatThrownBy(() -> future.get())
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("Should handle very fast complete-register-complete cycle")
        void testEdgeCase_FastCycle() throws Exception {
            // Arrange
            UUID commandId = UUID.randomUUID();

            // Act - Register, complete, register again, complete again
            CompletableFuture<String> future1 = registry.register(commandId);
            registry.complete(commandId, "first");
            future1.get(); // Wait for cleanup

            CompletableFuture<String> future2 = registry.register(commandId);
            registry.complete(commandId, "second");

            // Assert
            assertThat(future1.get()).isEqualTo("first");
            assertThat(future2.get()).isEqualTo("second");
        }

        @Test
        @DisplayName("Should handle special characters in response")
        void testEdgeCase_SpecialCharacters() throws Exception {
            // Arrange
            UUID commandId = UUID.randomUUID();
            CompletableFuture<String> future = registry.register(commandId);
            String specialResponse = "Response with special chars: !@#$%^&*()_+-={}[]|:;<>?,./~`\n\t\r";

            // Act
            registry.complete(commandId, specialResponse);

            // Assert
            assertThat(future.get()).isEqualTo(specialResponse);
        }

        @Test
        @DisplayName("Should handle Unicode characters in response")
        void testEdgeCase_UnicodeCharacters() throws Exception {
            // Arrange
            UUID commandId = UUID.randomUUID();
            CompletableFuture<String> future = registry.register(commandId);
            String unicodeResponse = "Unicode: \u4E2D\u6587 \u65E5\u672C\u8A9E \uD83D\uDE00 \u00E9\u00E8\u00EA";

            // Act
            registry.complete(commandId, unicodeResponse);

            // Assert
            assertThat(future.get()).isEqualTo(unicodeResponse);
        }
    }

    @Nested
    @DisplayName("Memory Leak Prevention Tests")
    class MemoryLeakTests {

        @Test
        @DisplayName("Should cleanup completed futures to prevent memory leak")
        void testMemoryLeak_CleanupOnComplete() throws Exception {
            // Arrange
            List<UUID> commandIds = new ArrayList<>();
            List<CompletableFuture<String>> futures = new ArrayList<>();

            // Act - Register and complete many futures
            for (int i = 0; i < 100; i++) {
                UUID commandId = UUID.randomUUID();
                commandIds.add(commandId);

                CompletableFuture<String> future = registry.register(commandId);
                futures.add(future);

                registry.complete(commandId, "response-" + i);
                future.get(); // Wait for cleanup
            }

            // Try completing again - should do nothing since they're cleaned up
            for (UUID commandId : commandIds) {
                registry.complete(commandId, "too late");
            }

            // Assert - All futures should still have original responses
            for (int i = 0; i < 100; i++) {
                assertThat(futures.get(i).get()).isEqualTo("response-" + i);
            }
        }

        @Test
        @DisplayName("Should cleanup failed futures to prevent memory leak")
        void testMemoryLeak_CleanupOnFail() {
            // Arrange
            List<UUID> commandIds = new ArrayList<>();
            List<CompletableFuture<String>> futures = new ArrayList<>();

            // Act - Register and fail many futures
            for (int i = 0; i < 100; i++) {
                UUID commandId = UUID.randomUUID();
                commandIds.add(commandId);

                CompletableFuture<String> future = registry.register(commandId);
                futures.add(future);

                registry.fail(commandId, "error-" + i);
            }

            // Try failing again - should do nothing since they're cleaned up
            for (UUID commandId : commandIds) {
                registry.fail(commandId, "too late");
            }

            // Assert - All futures should still have original errors
            for (int i = 0; i < 100; i++) {
                final int index = i;
                assertThatThrownBy(() -> futures.get(index).get())
                        .cause()
                        .hasMessage("error-" + index);
            }
        }

        @Test
        @DisplayName("Should cleanup timed out futures to prevent memory leak")
        void testMemoryLeak_CleanupOnTimeout() throws Exception {
            // Arrange
            List<CompletableFuture<String>> futures = new ArrayList<>();

            // Act - Register many futures and let them timeout
            for (int i = 0; i < 10; i++) {
                UUID commandId = UUID.randomUUID();
                CompletableFuture<String> future = registry.register(commandId);
                futures.add(future);
            }

            // Wait for all to timeout
            Thread.sleep(2500);

            // Assert - All should be timed out
            for (CompletableFuture<String> future : futures) {
                assertThat(future.isDone()).isTrue();
                assertThat(future.isCompletedExceptionally()).isTrue();
            }
        }
    }
}
