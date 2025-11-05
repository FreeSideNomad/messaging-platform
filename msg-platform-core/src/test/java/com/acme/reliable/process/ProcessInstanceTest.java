package com.acme.reliable.process;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for ProcessInstance record */
class ProcessInstanceTest {

  @Nested
  @DisplayName("Factory Method Tests")
  class FactoryMethodTests {

    @Test
    @DisplayName("create - should create new process instance")
    void testCreate() {
      UUID processId = UUID.randomUUID();
      Map<String, Object> data = Map.of("key", "value");

      ProcessInstance instance =
          ProcessInstance.create(processId, "OrderProcess", "order-123", "step1", data);

      assertThat(instance.processId()).isEqualTo(processId);
      assertThat(instance.processType()).isEqualTo("OrderProcess");
      assertThat(instance.businessKey()).isEqualTo("order-123");
      assertThat(instance.status()).isEqualTo(ProcessStatus.NEW);
      assertThat(instance.currentStep()).isEqualTo("step1");
      assertThat(instance.data()).containsEntry("key", "value");
      assertThat(instance.retries()).isEqualTo(0);
      assertThat(instance.createdAt()).isNotNull();
      assertThat(instance.updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("create - should create defensive copy of data")
    void testCreateDefensiveCopy() {
      Map<String, Object> original = new java.util.HashMap<>();
      original.put("key", "value");

      ProcessInstance instance =
          ProcessInstance.create(UUID.randomUUID(), "TestProcess", "key", "step1", original);

      // Modify original
      original.put("key", "modified");

      // Instance should have original value
      assertThat(instance.data()).containsEntry("key", "value");
    }
  }

  @Nested
  @DisplayName("Mutation Tests")
  class MutationTests {

    @Test
    @DisplayName("withStatus - should return new instance with updated status")
    void testWithStatus() {
      ProcessInstance original = createTestInstance();

      ProcessInstance updated = original.withStatus(ProcessStatus.RUNNING);

      assertThat(updated.status()).isEqualTo(ProcessStatus.RUNNING);
      assertThat(updated.processId()).isEqualTo(original.processId());
      assertThat(updated.currentStep()).isEqualTo(original.currentStep());
      assertThat(updated.updatedAt()).isAfter(original.updatedAt());
    }

    @Test
    @DisplayName("withCurrentStep - should return new instance with updated step")
    void testWithCurrentStep() {
      ProcessInstance original = createTestInstance();

      ProcessInstance updated = original.withCurrentStep("step2");

      assertThat(updated.currentStep()).isEqualTo("step2");
      assertThat(updated.status()).isEqualTo(original.status());
      assertThat(updated.processId()).isEqualTo(original.processId());
    }

    @Test
    @DisplayName("withData - should return new instance with updated data")
    void testWithData() {
      ProcessInstance original = createTestInstance();
      Map<String, Object> newData = Map.of("newKey", "newValue");

      ProcessInstance updated = original.withData(newData);

      assertThat(updated.data()).containsEntry("newKey", "newValue");
      assertThat(updated.data()).doesNotContainKey("key");
      assertThat(updated.processId()).isEqualTo(original.processId());
    }

    @Test
    @DisplayName("withRetries - should return new instance with updated retries")
    void testWithRetries() {
      ProcessInstance original = createTestInstance();

      ProcessInstance updated = original.withRetries(5);

      assertThat(updated.retries()).isEqualTo(5);
      assertThat(updated.processId()).isEqualTo(original.processId());
    }

    @Test
    @DisplayName("update - should update multiple fields at once")
    void testUpdate() {
      ProcessInstance original = createTestInstance();
      Map<String, Object> newData = Map.of("result", "success");

      ProcessInstance updated = original.update(ProcessStatus.SUCCEEDED, "final", newData, 3);

      assertThat(updated.status()).isEqualTo(ProcessStatus.SUCCEEDED);
      assertThat(updated.currentStep()).isEqualTo("final");
      assertThat(updated.data()).containsEntry("result", "success");
      assertThat(updated.retries()).isEqualTo(3);
      assertThat(updated.processId()).isEqualTo(original.processId());
      assertThat(updated.processType()).isEqualTo(original.processType());
      assertThat(updated.businessKey()).isEqualTo(original.businessKey());
    }
  }

  @Nested
  @DisplayName("Immutability Tests")
  class ImmutabilityTests {

    @Test
    @DisplayName("withStatus - should not modify original")
    void testWithStatusImmutability() {
      ProcessInstance original = createTestInstance();
      ProcessStatus originalStatus = original.status();

      original.withStatus(ProcessStatus.FAILED);

      assertThat(original.status()).isEqualTo(originalStatus);
    }

    @Test
    @DisplayName("withData - should create defensive copy")
    void testWithDataDefensiveCopy() {
      ProcessInstance original = createTestInstance();
      Map<String, Object> mutableData = new java.util.HashMap<>();
      mutableData.put("key", "value");

      ProcessInstance updated = original.withData(mutableData);

      // Modify external map
      mutableData.put("key", "modified");

      // Instance should have original value
      assertThat(updated.data()).containsEntry("key", "value");
    }
  }

  @Nested
  @DisplayName("Timestamp Tests")
  class TimestampTests {

    @Test
    @DisplayName("withStatus - should update updatedAt timestamp")
    void testWithStatusUpdatesTimestamp() throws InterruptedException {
      ProcessInstance original = createTestInstance();
      Thread.sleep(10);

      ProcessInstance updated = original.withStatus(ProcessStatus.RUNNING);

      assertThat(updated.updatedAt()).isAfter(original.updatedAt());
      assertThat(updated.createdAt()).isEqualTo(original.createdAt());
    }

    @Test
    @DisplayName("create - createdAt and updatedAt should be equal")
    void testCreateTimestamps() {
      ProcessInstance instance = createTestInstance();

      assertThat(instance.createdAt()).isEqualTo(instance.updatedAt());
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("create - should handle empty data")
    void testCreateEmptyData() {
      ProcessInstance instance =
          ProcessInstance.create(UUID.randomUUID(), "Process", "key", "step1", Map.of());

      assertThat(instance.data()).isEmpty();
    }

    @Test
    @DisplayName("withData - should handle empty map")
    void testWithDataEmpty() {
      ProcessInstance original = createTestInstance();

      ProcessInstance updated = original.withData(Map.of());

      assertThat(updated.data()).isEmpty();
    }

    @Test
    @DisplayName("withRetries - should handle zero retries")
    void testWithRetriesZero() {
      ProcessInstance original = createTestInstance();

      ProcessInstance updated = original.withRetries(0);

      assertThat(updated.retries()).isEqualTo(0);
    }

    @Test
    @DisplayName("withCurrentStep - should handle null step")
    void testWithCurrentStepNull() {
      ProcessInstance original = createTestInstance();

      ProcessInstance updated = original.withCurrentStep(null);

      assertThat(updated.currentStep()).isNull();
    }
  }

  // Helper method
  private ProcessInstance createTestInstance() {
    return ProcessInstance.create(
        UUID.randomUUID(), "TestProcess", "test-key", "initial", Map.of("key", "value"));
  }
}
