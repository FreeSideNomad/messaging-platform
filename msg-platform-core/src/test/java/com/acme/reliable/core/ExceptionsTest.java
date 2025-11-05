package com.acme.reliable.core;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for core exception classes */
class ExceptionsTest {

  @Nested
  @DisplayName("PermanentException Tests")
  class PermanentExceptionTests {

    @Test
    @DisplayName("Should create PermanentException with message")
    void testCreateWithMessage() {
      PermanentException exception = new PermanentException("Permanent error");

      assertThat(exception.getMessage()).isEqualTo("Permanent error");
      assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should be throwable")
    void testThrowable() {
      assertThatThrownBy(
              () -> {
                throw new PermanentException("Test error");
              })
          .isInstanceOf(PermanentException.class)
          .hasMessage("Test error");
    }

    @Test
    @DisplayName("Should have correct class hierarchy")
    void testHierarchy() {
      PermanentException exception = new PermanentException("Test");

      assertThat(exception).isInstanceOf(RuntimeException.class);
      assertThat(exception).isInstanceOf(Exception.class);
      assertThat(exception).isInstanceOf(Throwable.class);
    }
  }

  @Nested
  @DisplayName("TransientException Tests")
  class TransientExceptionTests {

    @Test
    @DisplayName("Should create TransientException with message")
    void testCreateWithMessage() {
      TransientException exception = new TransientException("Transient error");

      assertThat(exception.getMessage()).isEqualTo("Transient error");
      assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should be throwable")
    void testThrowable() {
      assertThatThrownBy(
              () -> {
                throw new TransientException("Network timeout");
              })
          .isInstanceOf(TransientException.class)
          .hasMessage("Network timeout");
    }

    @Test
    @DisplayName("Should have correct class hierarchy")
    void testHierarchy() {
      TransientException exception = new TransientException("Test");

      assertThat(exception).isInstanceOf(RuntimeException.class);
      assertThat(exception).isInstanceOf(Exception.class);
      assertThat(exception).isInstanceOf(Throwable.class);
    }

    @Test
    @DisplayName("Should be catchable as RuntimeException")
    void testCatchable() {
      try {
        throw new TransientException("Test");
      } catch (RuntimeException e) {
        assertThat(e).isInstanceOf(TransientException.class);
      }
    }
  }

  @Nested
  @DisplayName("RetryableBusinessException Tests")
  class RetryableBusinessExceptionTests {

    @Test
    @DisplayName("Should create RetryableBusinessException with message")
    void testCreateWithMessage() {
      RetryableBusinessException exception = new RetryableBusinessException("Business rule error");

      assertThat(exception.getMessage()).isEqualTo("Business rule error");
      assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should be throwable")
    void testThrowable() {
      assertThatThrownBy(
              () -> {
                throw new RetryableBusinessException("Insufficient funds");
              })
          .isInstanceOf(RetryableBusinessException.class)
          .hasMessage("Insufficient funds");
    }

    @Test
    @DisplayName("Should have correct class hierarchy")
    void testHierarchy() {
      RetryableBusinessException exception = new RetryableBusinessException("Test");

      assertThat(exception).isInstanceOf(RuntimeException.class);
      assertThat(exception).isInstanceOf(Exception.class);
      assertThat(exception).isInstanceOf(Throwable.class);
    }

    @Test
    @DisplayName("Should be distinguishable from other exceptions")
    void testDistinguishable() {
      Exception exception = new RetryableBusinessException("Test");

      assertThat(exception).isNotInstanceOf(PermanentException.class);
      assertThat(exception).isNotInstanceOf(TransientException.class);
    }
  }

  @Nested
  @DisplayName("Exception Semantics Tests")
  class SemanticsTests {

    @Test
    @DisplayName("Should differentiate between exception types")
    void testDifferentiation() {
      Exception permanent = new PermanentException("Permanent");
      Exception transient_ = new TransientException("Transient");
      Exception retryable = new RetryableBusinessException("Retryable");

      assertThat(permanent).isNotInstanceOf(TransientException.class);
      assertThat(transient_).isNotInstanceOf(PermanentException.class);
      assertThat(retryable).isNotInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("All exceptions should be RuntimeExceptions")
    void testAllRuntimeExceptions() {
      assertThat(new PermanentException("Test")).isInstanceOf(RuntimeException.class);
      assertThat(new TransientException("Test")).isInstanceOf(RuntimeException.class);
      assertThat(new RetryableBusinessException("Test")).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should handle empty messages")
    void testEmptyMessages() {
      assertThatNoException()
          .isThrownBy(
              () -> {
                new PermanentException("");
                new TransientException("");
                new RetryableBusinessException("");
              });
    }

    @Test
    @DisplayName("Should handle null messages")
    void testNullMessages() {
      PermanentException permanent = new PermanentException(null);
      TransientException transient_ = new TransientException(null);
      RetryableBusinessException retryable = new RetryableBusinessException(null);

      assertThat(permanent.getMessage()).isNull();
      assertThat(transient_.getMessage()).isNull();
      assertThat(retryable.getMessage()).isNull();
    }
  }
}
