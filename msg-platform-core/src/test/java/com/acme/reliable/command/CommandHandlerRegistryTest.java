package com.acme.reliable.command;

import com.acme.reliable.process.CommandReply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for CommandHandlerRegistry
 */
class CommandHandlerRegistryTest {

    private CommandHandlerRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CommandHandlerRegistry();
    }

    @Nested
    @DisplayName("Handler Registration Tests")
    class HandlerRegistrationTests {

        @Test
        @DisplayName("registerHandler - should register handler successfully")
        void testRegisterHandler() {
            Function<CommandMessage, CommandReply> handler =
                    cmd -> CommandReply.completed(cmd.commandId(), cmd.correlationId(), Map.of());

            assertThatCode(() -> registry.registerHandler("CreateUser", handler))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("registerHandler - should throw exception when duplicate handler registered")
        void testRegisterDuplicateHandler() {
            Function<CommandMessage, CommandReply> handler1 =
                    cmd -> CommandReply.completed(cmd.commandId(), cmd.correlationId(), Map.of());
            Function<CommandMessage, CommandReply> handler2 =
                    cmd -> CommandReply.completed(cmd.commandId(), cmd.correlationId(), Map.of());

            registry.registerHandler("CreateUser", handler1);

            assertThatThrownBy(() -> registry.registerHandler("CreateUser", handler2))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Handler already registered for command type: CreateUser");
        }

        @Test
        @DisplayName("registerHandler - should allow different command types")
        void testRegisterMultipleHandlers() {
            Function<CommandMessage, CommandReply> handler1 =
                    cmd -> CommandReply.completed(cmd.commandId(), cmd.correlationId(), Map.of());
            Function<CommandMessage, CommandReply> handler2 =
                    cmd -> CommandReply.completed(cmd.commandId(), cmd.correlationId(), Map.of());

            registry.registerHandler("CreateUser", handler1);
            registry.registerHandler("DeleteUser", handler2);

            CommandMessage cmd1 =
                    new CommandMessage(UUID.randomUUID(), UUID.randomUUID(), "CreateUser", "{}");
            CommandMessage cmd2 =
                    new CommandMessage(UUID.randomUUID(), UUID.randomUUID(), "DeleteUser", "{}");

            assertThat(registry.handle(cmd1).isSuccess()).isTrue();
            assertThat(registry.handle(cmd2).isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("Command Handling Tests")
    class CommandHandlingTests {

        @Test
        @DisplayName("handle - should invoke registered handler")
        void testHandleInvokesHandler() {
            UUID commandId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();
            Map<String, Object> responseData = Map.of("result", "success");

            Function<CommandMessage, CommandReply> handler =
                    cmd -> {
                        assertThat(cmd.commandType()).isEqualTo("CreateUser");
                        assertThat(cmd.commandId()).isEqualTo(commandId);
                        return CommandReply.completed(cmd.commandId(), cmd.correlationId(), responseData);
                    };

            registry.registerHandler("CreateUser", handler);

            CommandMessage command =
                    new CommandMessage(commandId, correlationId, "CreateUser", "{\"name\":\"John\"}");

            CommandReply reply = registry.handle(command);

            assertThat(reply).isNotNull();
            assertThat(reply.isSuccess()).isTrue();
            assertThat(reply.commandId()).isEqualTo(commandId);
            assertThat(reply.correlationId()).isEqualTo(correlationId);
            assertThat(reply.data()).containsEntry("result", "success");
        }

        @Test
        @DisplayName("handle - should return failed reply for unregistered command")
        void testHandleUnregisteredCommand() {
            UUID commandId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();

            CommandMessage command = new CommandMessage(commandId, correlationId, "UnknownCommand", "{}");

            CommandReply reply = registry.handle(command);

            assertThat(reply).isNotNull();
            assertThat(reply.isFailure()).isTrue();
            assertThat(reply.commandId()).isEqualTo(commandId);
            assertThat(reply.correlationId()).isEqualTo(correlationId);
            assertThat(reply.error()).contains("No handler registered for command type: UnknownCommand");
        }

        @Test
        @DisplayName("handle - should return failed reply when handler throws exception")
        void testHandleHandlerThrowsException() {
            UUID commandId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();

            Function<CommandMessage, CommandReply> faultyHandler =
                    cmd -> {
                        throw new RuntimeException("Handler error");
                    };

            registry.registerHandler("FaultyCommand", faultyHandler);

            CommandMessage command = new CommandMessage(commandId, correlationId, "FaultyCommand", "{}");

            CommandReply reply = registry.handle(command);

            assertThat(reply).isNotNull();
            assertThat(reply.isFailure()).isTrue();
            assertThat(reply.commandId()).isEqualTo(commandId);
            assertThat(reply.correlationId()).isEqualTo(correlationId);
            assertThat(reply.error()).isEqualTo("Handler error");
        }

        @Test
        @DisplayName("handle - should handle null pointer exception in handler")
        void testHandleNullPointerException() {
            UUID commandId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();

            Function<CommandMessage, CommandReply> faultyHandler =
                    cmd -> {
                        throw new NullPointerException("Unexpected null");
                    };

            registry.registerHandler("NullCommand", faultyHandler);

            CommandMessage command = new CommandMessage(commandId, correlationId, "NullCommand", "{}");

            CommandReply reply = registry.handle(command);

            assertThat(reply).isNotNull();
            assertThat(reply.isFailure()).isTrue();
            assertThat(reply.error()).isEqualTo("Unexpected null");
        }

        @Test
        @DisplayName("handle - should handle exception with null message")
        void testHandleExceptionWithNullMessage() {
            UUID commandId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();

            Function<CommandMessage, CommandReply> faultyHandler =
                    cmd -> {
                        throw new RuntimeException();
                    };

            registry.registerHandler("ErrorCommand", faultyHandler);

            CommandMessage command = new CommandMessage(commandId, correlationId, "ErrorCommand", "{}");

            CommandReply reply = registry.handle(command);

            assertThat(reply).isNotNull();
            assertThat(reply.isFailure()).isTrue();
            assertThat(reply.error()).isNull();
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("should handle multiple commands sequentially")
        void testMultipleCommandsSequentially() {
            Function<CommandMessage, CommandReply> createHandler =
                    cmd ->
                            CommandReply.completed(
                                    cmd.commandId(), cmd.correlationId(), Map.of("action", "created"));
            Function<CommandMessage, CommandReply> updateHandler =
                    cmd ->
                            CommandReply.completed(
                                    cmd.commandId(), cmd.correlationId(), Map.of("action", "updated"));

            registry.registerHandler("CreateUser", createHandler);
            registry.registerHandler("UpdateUser", updateHandler);

            CommandMessage createCmd =
                    new CommandMessage(UUID.randomUUID(), UUID.randomUUID(), "CreateUser", "{}");
            CommandReply createReply = registry.handle(createCmd);
            assertThat(createReply.isSuccess()).isTrue();
            assertThat(createReply.data()).containsEntry("action", "created");

            CommandMessage updateCmd =
                    new CommandMessage(UUID.randomUUID(), UUID.randomUUID(), "UpdateUser", "{}");
            CommandReply updateReply = registry.handle(updateCmd);
            assertThat(updateReply.isSuccess()).isTrue();
            assertThat(updateReply.data()).containsEntry("action", "updated");
        }

        @Test
        @DisplayName("should maintain handler state across invocations")
        void testHandlerState() {
            int[] counter = {0};
            Function<CommandMessage, CommandReply> statefulHandler =
                    cmd -> {
                        counter[0]++;
                        return CommandReply.completed(
                                cmd.commandId(), cmd.correlationId(), Map.of("count", counter[0]));
                    };

            registry.registerHandler("CountCommand", statefulHandler);

            CommandMessage cmd1 =
                    new CommandMessage(UUID.randomUUID(), UUID.randomUUID(), "CountCommand", "{}");
            CommandReply reply1 = registry.handle(cmd1);
            assertThat(reply1.data()).containsEntry("count", 1);

            CommandMessage cmd2 =
                    new CommandMessage(UUID.randomUUID(), UUID.randomUUID(), "CountCommand", "{}");
            CommandReply reply2 = registry.handle(cmd2);
            assertThat(reply2.data()).containsEntry("count", 2);
        }

        @Test
        @DisplayName("should handle commands with complex payloads")
        void testComplexPayload() {
            Function<CommandMessage, CommandReply> handler =
                    cmd -> {
                        assertThat(cmd.payload()).contains("name");
                        assertThat(cmd.payload()).contains("email");
                        return CommandReply.completed(
                                cmd.commandId(), cmd.correlationId(), Map.of("processed", true));
                    };

            registry.registerHandler("ComplexCommand", handler);

            CommandMessage command =
                    new CommandMessage(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            "ComplexCommand",
                            "{\"name\":\"John Doe\",\"email\":\"john@example.com\"}");

            CommandReply reply = registry.handle(command);

            assertThat(reply.isSuccess()).isTrue();
            assertThat(reply.data()).containsEntry("processed", true);
        }
    }
}
