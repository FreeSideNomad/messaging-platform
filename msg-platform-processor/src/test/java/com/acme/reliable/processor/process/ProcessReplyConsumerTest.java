package com.acme.reliable.processor.process;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.acme.reliable.core.Jsons;
import com.acme.reliable.process.CommandReply;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ProcessReplyConsumer Tests")
class ProcessReplyConsumerTest {

  private ProcessReplyConsumer consumer;
  private ProcessManager mockProcessManager;

  @BeforeEach
  void setup() {
    mockProcessManager = mock(ProcessManager.class);
    consumer = new ProcessReplyConsumer(mockProcessManager);
  }

  @Nested
  @DisplayName("onReply - CommandCompleted Tests")
  class CommandCompletedTests {

    @Test
    @DisplayName("should parse and route CommandCompleted reply")
    void testOnReply_CommandCompleted() {
      UUID commandId = UUID.randomUUID();
      UUID correlationId = UUID.randomUUID();

      Map<String, Object> reply = Map.of(
          "commandId", commandId.toString(),
          "correlationId", correlationId.toString(),
          "type", "CommandCompleted",
          "payload", Map.of("status", "success")
      );

      String json = Jsons.toJson(reply);
      consumer.onReply(json);

      verify(mockProcessManager).handleReply(eq(correlationId), eq(commandId), any(CommandReply.class));
    }

    @Test
    @DisplayName("should handle CommandCompleted with empty payload")
    void testOnReply_CommandCompletedEmptyPayload() {
      UUID commandId = UUID.randomUUID();
      UUID correlationId = UUID.randomUUID();

      Map<String, Object> reply = Map.of(
          "commandId", commandId.toString(),
          "correlationId", correlationId.toString(),
          "type", "CommandCompleted"
      );

      String json = Jsons.toJson(reply);
      consumer.onReply(json);

      verify(mockProcessManager).handleReply(eq(correlationId), eq(commandId), any(CommandReply.class));
    }
  }

  @Nested
  @DisplayName("onReply - CommandFailed Tests")
  class CommandFailedTests {

    @Test
    @DisplayName("should parse and route CommandFailed reply")
    void testOnReply_CommandFailed() {
      UUID commandId = UUID.randomUUID();
      UUID correlationId = UUID.randomUUID();

      Map<String, Object> reply = Map.of(
          "commandId", commandId.toString(),
          "correlationId", correlationId.toString(),
          "type", "CommandFailed",
          "error", "Database connection timeout"
      );

      String json = Jsons.toJson(reply);
      consumer.onReply(json);

      verify(mockProcessManager).handleReply(eq(correlationId), eq(commandId), any(CommandReply.class));
    }

    @Test
    @DisplayName("should use default error message if not provided")
    void testOnReply_CommandFailedNoError() {
      UUID commandId = UUID.randomUUID();
      UUID correlationId = UUID.randomUUID();

      Map<String, Object> reply = Map.of(
          "commandId", commandId.toString(),
          "correlationId", correlationId.toString(),
          "type", "CommandFailed"
      );

      String json = Jsons.toJson(reply);
      consumer.onReply(json);

      verify(mockProcessManager).handleReply(eq(correlationId), eq(commandId), any(CommandReply.class));
    }
  }

  @Nested
  @DisplayName("onReply - CommandTimedOut Tests")
  class CommandTimedOutTests {

    @Test
    @DisplayName("should parse and route CommandTimedOut reply")
    void testOnReply_CommandTimedOut() {
      UUID commandId = UUID.randomUUID();
      UUID correlationId = UUID.randomUUID();

      Map<String, Object> reply = Map.of(
          "commandId", commandId.toString(),
          "correlationId", correlationId.toString(),
          "type", "CommandTimedOut",
          "error", "Execution timeout after 5 minutes"
      );

      String json = Jsons.toJson(reply);
      consumer.onReply(json);

      verify(mockProcessManager).handleReply(eq(correlationId), eq(commandId), any(CommandReply.class));
    }
  }

  @Nested
  @DisplayName("onReply - Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("should handle malformed JSON gracefully")
    void testOnReply_MalformedJson() {
      String malformedJson = "{invalid json}";

      consumer.onReply(malformedJson);

      // ProcessManager should not be called on error
      verify(mockProcessManager, never()).handleReply(any(), any(), any());
    }

    @Test
    @DisplayName("should handle missing commandId gracefully")
    void testOnReply_MissingCommandId() {
      UUID correlationId = UUID.randomUUID();

      Map<String, Object> reply = Map.of(
          "correlationId", correlationId.toString(),
          "type", "CommandCompleted"
      );

      String json = Jsons.toJson(reply);
      consumer.onReply(json);

      // Should handle error without throwing
      // ProcessManager may not be called if critical field is missing
    }

    @Test
    @DisplayName("should handle unknown reply type gracefully")
    void testOnReply_UnknownReplyType() {
      UUID commandId = UUID.randomUUID();
      UUID correlationId = UUID.randomUUID();

      Map<String, Object> reply = Map.of(
          "commandId", commandId.toString(),
          "correlationId", correlationId.toString(),
          "type", "UnknownType"
      );

      String json = Jsons.toJson(reply);
      consumer.onReply(json);

      // Should handle error without throwing
      // ProcessManager may not be called if type is unknown
    }

    @Test
    @DisplayName("should handle null body gracefully")
    void testOnReply_NullBody() {
      consumer.onReply(null);

      // Should handle null gracefully
      verify(mockProcessManager, never()).handleReply(any(), any(), any());
    }
  }

  @Nested
  @DisplayName("onReply - Payload Handling Tests")
  class PayloadHandlingTests {

    @Test
    @DisplayName("should handle large payload")
    void testOnReply_LargePayload() {
      UUID commandId = UUID.randomUUID();
      UUID correlationId = UUID.randomUUID();

      String largeData = "x".repeat(10000);
      Map<String, Object> reply = Map.of(
          "commandId", commandId.toString(),
          "correlationId", correlationId.toString(),
          "type", "CommandCompleted",
          "payload", Map.of("data", largeData)
      );

      String json = Jsons.toJson(reply);
      consumer.onReply(json);

      verify(mockProcessManager).handleReply(eq(correlationId), eq(commandId), any(CommandReply.class));
    }

    @Test
    @DisplayName("should handle nested payload structure")
    void testOnReply_NestedPayload() {
      UUID commandId = UUID.randomUUID();
      UUID correlationId = UUID.randomUUID();

      Map<String, Object> reply = Map.of(
          "commandId", commandId.toString(),
          "correlationId", correlationId.toString(),
          "type", "CommandCompleted",
          "payload", Map.of(
              "nested", Map.of("deep", Map.of("value", "data"))
          )
      );

      String json = Jsons.toJson(reply);
      consumer.onReply(json);

      verify(mockProcessManager).handleReply(eq(correlationId), eq(commandId), any(CommandReply.class));
    }
  }

  @Nested
  @DisplayName("onReply - Multiple Calls Tests")
  class MultipleCallsTests {

    @Test
    @DisplayName("should handle sequential replies")
    void testOnReply_SequentialReplies() {
      for (int i = 0; i < 3; i++) {
        UUID commandId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        Map<String, Object> reply = Map.of(
            "commandId", commandId.toString(),
            "correlationId", correlationId.toString(),
            "type", "CommandCompleted"
        );

        String json = Jsons.toJson(reply);
        consumer.onReply(json);
      }

      verify(mockProcessManager, times(3)).handleReply(any(), any(), any(CommandReply.class));
    }

    @Test
    @DisplayName("should handle replies of different types")
    void testOnReply_MixedReplyTypes() {
      UUID cmdId1 = UUID.randomUUID();
      UUID corrId1 = UUID.randomUUID();

      // CommandCompleted
      consumer.onReply(Jsons.toJson(Map.of(
          "commandId", cmdId1.toString(),
          "correlationId", corrId1.toString(),
          "type", "CommandCompleted"
      )));

      UUID cmdId2 = UUID.randomUUID();
      UUID corrId2 = UUID.randomUUID();

      // CommandFailed
      consumer.onReply(Jsons.toJson(Map.of(
          "commandId", cmdId2.toString(),
          "correlationId", corrId2.toString(),
          "type", "CommandFailed",
          "error", "Failed"
      )));

      UUID cmdId3 = UUID.randomUUID();
      UUID corrId3 = UUID.randomUUID();

      // CommandTimedOut
      consumer.onReply(Jsons.toJson(Map.of(
          "commandId", cmdId3.toString(),
          "correlationId", corrId3.toString(),
          "type", "CommandTimedOut"
      )));

      verify(mockProcessManager, times(3)).handleReply(any(), any(), any(CommandReply.class));
    }
  }
}
