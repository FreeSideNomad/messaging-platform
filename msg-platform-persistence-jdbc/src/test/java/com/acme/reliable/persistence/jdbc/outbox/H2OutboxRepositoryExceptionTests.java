package com.acme.reliable.persistence.jdbc.outbox;

import com.acme.reliable.persistence.jdbc.H2OutboxRepository;
import com.acme.reliable.persistence.jdbc.H2RepositoryFaultyTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exception handling tests for H2OutboxRepository.
 * Tests catch blocks by operating on a database with no tables.
 * All operations should trigger SQLException which is wrapped as RuntimeException.
 */
class H2OutboxRepositoryExceptionTests extends H2RepositoryFaultyTestBase {

    private H2OutboxRepository repository;

    private void setupRepository() {
        repository = new H2OutboxRepository(getDataSource());
    }

    @Nested
    @DisplayName("Insert Operations Exception Handling")
    class InsertExceptionTests {

        @Test
        @DisplayName("insertReturningId should throw RuntimeException when table doesn't exist")
        void testInsertReturningIdTableNotFound() {
            setupRepository();

            assertThatThrownBy(
                    () -> repository.insertReturningId("events", "test", "key", "type", "{}", "{}"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("insert should throw RuntimeException when table doesn't exist")
        void testInsertTableNotFound() {
            setupRepository();

            assertThatThrownBy(
                    () -> repository.insert(1L, "events", "test", "key", "type", "{}", "{}", "NEW", 0))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("Claim Operations Exception Handling")
    class ClaimExceptionTests {

        @Test
        @DisplayName("claimIfNew should throw RuntimeException when table doesn't exist")
        void testClaimIfNewTableNotFound() {
            setupRepository();

            assertThatThrownBy(() -> repository.claimIfNew(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to claim");
        }
    }

    @Nested
    @DisplayName("Batch Sweep Exception Handling")
    class SweepExceptionTests {

        @Test
        @DisplayName("sweepBatch should throw RuntimeException when table doesn't exist")
        void testSweepBatchTableNotFound() {
            setupRepository();

            assertThatThrownBy(() -> repository.sweepBatch(100))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to sweep");
        }
    }

    @Nested
    @DisplayName("Status Update Exception Handling")
    class StatusUpdateExceptionTests {

        @Test
        @DisplayName("markPublished should throw RuntimeException when table doesn't exist")
        void testMarkPublishedTableNotFound() {
            setupRepository();

            assertThatThrownBy(() -> repository.markPublished(1L))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("markFailed should throw RuntimeException when table doesn't exist")
        void testMarkFailedTableNotFound() {
            setupRepository();

            assertThatThrownBy(
                    () -> repository.markFailed(1L, "error", Instant.now().plus(Duration.ofSeconds(30))))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("reschedule should throw RuntimeException when table doesn't exist")
        void testRescheduleTableNotFound() {
            setupRepository();

            assertThatThrownBy(() -> repository.reschedule(1L, 5000, "error"))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("Recovery Exception Handling")
    class RecoveryExceptionTests {

        @Test
        @DisplayName("recoverStuck should throw RuntimeException when table doesn't exist")
        void testRecoverStuckTableNotFound() {
            setupRepository();

            assertThatThrownBy(() -> repository.recoverStuck(Duration.ofHours(1)))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}
