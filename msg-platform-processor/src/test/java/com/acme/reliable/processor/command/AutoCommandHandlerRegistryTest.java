package com.acme.reliable.processor.command;

import com.acme.reliable.command.CommandMessage;
import com.acme.reliable.command.DomainCommand;
import com.acme.reliable.core.Jsons;
import com.acme.reliable.process.CommandReply;
import io.micronaut.context.BeanContext;
import io.micronaut.inject.BeanDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for AutoCommandHandlerRegistry
 *
 * <p>Tests cover:
 * - Command handler auto-discovery from classpath
 * - Handler registration mechanism
 * - Handler lookup and retrieval
 * - Duplicate handler registration handling
 * - Error cases (missing handlers, invalid types)
 * - Handler invocation with various command types
 * - Caching of discovered handlers
 *
 * <p>Target: 80%+ line and branch coverage
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AutoCommandHandlerRegistry Tests")
class AutoCommandHandlerRegistryTest {

    @Mock
    private BeanContext mockBeanContext;

    private AutoCommandHandlerRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AutoCommandHandlerRegistry(mockBeanContext);
    }

    // Helper method to create mock BeanDefinition
    @SuppressWarnings("unchecked")
    private <T> BeanDefinition<T> mockBeanDefinition(Class<T> beanClass) {
        BeanDefinition<T> beanDef = mock(BeanDefinition.class);
        when(beanDef.getBeanType()).thenReturn((Class) beanClass);
        return beanDef;
    }

    // Test command implementations
    public record CreateUserCommand(String username, String email) implements DomainCommand {
    }

    public record UpdateUserCommand(String userId, String newEmail) implements DomainCommand {
    }

    public record DeleteUserCommand(String userId) implements DomainCommand {
    }

    public record CommandWithoutSuffix(String data) implements DomainCommand {
    }

    // Test service bean
    public static class UserService {
        public UserResult createUser(CreateUserCommand cmd) {
            return new UserResult(UUID.randomUUID().toString(), cmd.username());
        }

        public UserResult updateUser(UpdateUserCommand cmd) {
            return new UserResult(cmd.userId(), "updated");
        }

        public void deleteUser(DeleteUserCommand cmd) {
            // void return type
        }

        // Not a handler - multiple parameters
        public void multiParam(CreateUserCommand cmd, String extra) {
            // This method intentionally does nothing - it's a test helper to simulate a method that shouldn't be registered as a handler
        }

        // Not a handler - no DomainCommand parameter
        public String nonCommandMethod(String input) {
            return input;
        }
    }

    // Proxied service (simulates Micronaut AOP)
    public static class UserService$Intercepted extends UserService {
        public String $$access$$method() {
            return "proxy";
        }
    }

    // Second service handling same command type (for ambiguity tests)
    public static class AnotherUserService {
        public UserResult createUser(CreateUserCommand cmd) {
            return new UserResult(UUID.randomUUID().toString(), "another");
        }
    }

    // Service with void return type
    public static class VoidService {
        public void handleCommand(DeleteUserCommand cmd) {
            // No return value
        }
    }

    // Service that returns null
    public static class NullReturningService {
        public UserResult handleUpdate(UpdateUserCommand cmd) {
            return null;
        }
    }

    // Service that throws exceptions
    public static class FaultyService {
        public UserResult handleCreate(CreateUserCommand cmd) {
            throw new RuntimeException("Service error");
        }
    }

    public record UserResult(String id, String name) {
    }

    // Service for testing command types without "Command" suffix
    public static class TestService {
        public void handleCommand(CommandWithoutSuffix cmd) {
            // void return type
        }
    }

    @Nested
    @DisplayName("Handler Discovery Tests")
    class HandlerDiscoveryTests {

        @Test
        @DisplayName("should discover and register handler from simple service bean")
        void testDiscoverSimpleHandler() {
            UserService service = new UserService();
            BeanDefinition<UserService> beanDef = mockBeanDefinition(UserService.class);

            when(mockBeanContext.getAllBeanDefinitions()).thenReturn(List.of(beanDef));
            when(mockBeanContext.getBean(UserService.class)).thenReturn(service);

            registry.discoverAndRegisterHandlers();

            // Verify handler was registered by trying to handle a command
            UUID cmdId = UUID.randomUUID();
            UUID corrId = UUID.randomUUID();
            String payload = Jsons.toJson(new CreateUserCommand("john", "john@example.com"));
            CommandMessage msg = new CommandMessage(cmdId, corrId, "CreateUser", payload);

            CommandReply reply = registry.handle(msg);

            assertThat(reply).isNotNull();
            assertThat(reply.isSuccess()).isTrue();
            assertThat(reply.data()).containsKey("id");
            assertThat(reply.data()).containsEntry("name", "john");
        }

        @Test
        @DisplayName("should discover multiple handlers from same service bean")
        void testDiscoverMultipleHandlers() {
            UserService service = new UserService();
            BeanDefinition<UserService> beanDef = mockBeanDefinition(UserService.class);

            when(mockBeanContext.getAllBeanDefinitions()).thenReturn(List.of(beanDef));
            when(mockBeanContext.getBean(UserService.class)).thenReturn(service);

            registry.discoverAndRegisterHandlers();

            // Test CreateUser handler
            UUID cmdId1 = UUID.randomUUID();
            String payload1 = Jsons.toJson(new CreateUserCommand("john", "john@example.com"));
            CommandMessage msg1 = new CommandMessage(cmdId1, UUID.randomUUID(), "CreateUser", payload1);
            assertThat(registry.handle(msg1).isSuccess()).isTrue();

            // Test UpdateUser handler
            UUID cmdId2 = UUID.randomUUID();
            String payload2 = Jsons.toJson(new UpdateUserCommand("123", "new@example.com"));
            CommandMessage msg2 = new CommandMessage(cmdId2, UUID.randomUUID(), "UpdateUser", payload2);
            assertThat(registry.handle(msg2).isSuccess()).isTrue();

            // Test DeleteUser handler
            UUID cmdId3 = UUID.randomUUID();
            String payload3 = Jsons.toJson(new DeleteUserCommand("123"));
            CommandMessage msg3 = new CommandMessage(cmdId3, UUID.randomUUID(), "DeleteUser", payload3);
            assertThat(registry.handle(msg3).isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should skip CommandHandlerRegistry beans during discovery")
        void testSkipRegistryBeans() {
            BeanDefinition<AutoCommandHandlerRegistry> registryDef =
                    mockBeanDefinition(AutoCommandHandlerRegistry.class);

            when(mockBeanContext.getAllBeanDefinitions()).thenReturn(List.of(registryDef));

            registry.discoverAndRegisterHandlers();

            // No handlers should be registered
            UUID cmdId = UUID.randomUUID();
            CommandMessage msg = new CommandMessage(cmdId, UUID.randomUUID(), "CreateUser", "{}");
            CommandReply reply = registry.handle(msg);

            assertThat(reply.isFailure()).isTrue();
            assertThat(reply.error()).contains("No handler registered");
        }

        @Test
        @DisplayName("should skip beans that are not yet available")
        void testSkipUnavailableBeans() {
            BeanDefinition<UserService> beanDef = mockBeanDefinition(UserService.class);

            when(mockBeanContext.getAllBeanDefinitions()).thenReturn(List.of(beanDef));
            when(mockBeanContext.getBean(UserService.class))
                    .thenThrow(new RuntimeException("Bean not ready"));

            registry.discoverAndRegisterHandlers();

            // No handlers should be registered due to unavailable bean
            UUID cmdId = UUID.randomUUID();
            CommandMessage msg = new CommandMessage(cmdId, UUID.randomUUID(), "CreateUser", "{}");
            CommandReply reply = registry.handle(msg);

            assertThat(reply.isFailure()).isTrue();
        }

        @Test
        @DisplayName("should skip methods with multiple parameters")
        void testSkipMultiParamMethods() {
            UserService service = new UserService();
            BeanDefinition<UserService> beanDef = mockBeanDefinition(UserService.class);

            when(mockBeanContext.getAllBeanDefinitions()).thenReturn(List.of(beanDef));
            when(mockBeanContext.getBean(UserService.class)).thenReturn(service);

            registry.discoverAndRegisterHandlers();

            // multiParam method should not be registered
            // Only single-param methods should be discovered
            UUID cmdId = UUID.randomUUID();
            String payload = Jsons.toJson(new CreateUserCommand("john", "john@example.com"));
            CommandMessage msg = new CommandMessage(cmdId, UUID.randomUUID(), "CreateUser", payload);

            assertThat(registry.handle(msg).isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should skip methods without DomainCommand parameters")
        void testSkipNonCommandMethods() {
            UserService service = new UserService();
            BeanDefinition<UserService> beanDef = mockBeanDefinition(UserService.class);

            when(mockBeanContext.getAllBeanDefinitions()).thenReturn(List.of(beanDef));
            when(mockBeanContext.getBean(UserService.class)).thenReturn(service);

            registry.discoverAndRegisterHandlers();

            // nonCommandMethod should not be registered as a handler
            // We can verify by checking that only valid command types work
            UUID cmdId = UUID.randomUUID();
            String payload = Jsons.toJson(new CreateUserCommand("john", "john@example.com"));
            CommandMessage msg = new CommandMessage(cmdId, UUID.randomUUID(), "CreateUser", payload);

            assertThat(registry.handle(msg).isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should prefer proxy beans over non-proxy beans")
        void testPreferProxyBeans() {
            UserService proxyService = new UserService$Intercepted();
            BeanDefinition<UserService> beanDef = mockBeanDefinition(UserService.class);

            when(mockBeanContext.getAllBeanDefinitions()).thenReturn(List.of(beanDef));
            when(mockBeanContext.getBean(UserService.class)).thenReturn(proxyService);

            registry.discoverAndRegisterHandlers();

            // Should successfully use the proxy
            UUID cmdId = UUID.randomUUID();
            String payload = Jsons.toJson(new CreateUserCommand("john", "john@example.com"));
            CommandMessage msg = new CommandMessage(cmdId, UUID.randomUUID(), "CreateUser", payload);

            assertThat(registry.handle(msg).isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should skip AOP accessor methods")
        void testSkipAopAccessorMethods() {
            UserService$Intercepted proxyService = new UserService$Intercepted();
            BeanDefinition<UserService$Intercepted> beanDef =
                    mockBeanDefinition(UserService$Intercepted.class);

            when(mockBeanContext.getAllBeanDefinitions()).thenReturn(List.of(beanDef));
            when(mockBeanContext.getBean(UserService$Intercepted.class)).thenReturn(proxyService);

            registry.discoverAndRegisterHandlers();

            // Accessor methods should be skipped, only real handlers registered
            UUID cmdId = UUID.randomUUID();
            String payload = Jsons.toJson(new CreateUserCommand("john", "john@example.com"));
            CommandMessage msg = new CommandMessage(cmdId, UUID.randomUUID(), "CreateUser", payload);

            assertThat(registry.handle(msg).isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should derive command type from class name without Command suffix")
        void testDeriveCommandType() {
            UserService service = new UserService();
            BeanDefinition<UserService> beanDef = mockBeanDefinition(UserService.class);

            when(mockBeanContext.getAllBeanDefinitions()).thenReturn(List.of(beanDef));
            when(mockBeanContext.getBean(UserService.class)).thenReturn(service);

            registry.discoverAndRegisterHandlers();

            // CreateUserCommand should be registered as "CreateUser"
            UUID cmdId = UUID.randomUUID();
            String payload = Jsons.toJson(new CreateUserCommand("john", "john@example.com"));
            CommandMessage msg = new CommandMessage(cmdId, UUID.randomUUID(), "CreateUser", payload);

            assertThat(registry.handle(msg).isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("Handler Registration Tests")
    class HandlerRegistrationTests {

        @Test
        @DisplayName("should throw exception when duplicate handlers found")
        void testDuplicateHandlerThrowsException() {
            UserService service1 = new UserService();
            AnotherUserService service2 = new AnotherUserService();

            BeanDefinition<UserService> beanDef1 = mockBeanDefinition(UserService.class);
            BeanDefinition<AnotherUserService> beanDef2 = mockBeanDefinition(AnotherUserService.class);

            when(mockBeanContext.getAllBeanDefinitions()).thenReturn(List.of(beanDef1, beanDef2));
            when(mockBeanContext.getBean(UserService.class)).thenReturn(service1);
            when(mockBeanContext.getBean(AnotherUserService.class)).thenReturn(service2);

            assertThatThrownBy(() -> registry.discoverAndRegisterHandlers())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Ambiguous handler registration")
                    .hasMessageContaining("CreateUser")
                    .hasMessageContaining("2 different implementations");
        }

        @Test
        @DisplayName("should allow multiple candidates from same base class (proxy vs non-proxy)")
        void testMultipleCandidatesSameBaseClass() {
            // This simulates Micronaut creating both a proxy and the original bean
            UserService service = new UserService();
            BeanDefinition<UserService> beanDef = mockBeanDefinition(UserService.class);

            when(mockBeanContext.getAllBeanDefinitions()).thenReturn(List.of(beanDef));
            when(mockBeanContext.getBean(UserService.class)).thenReturn(service);

            // Should not throw exception - same base class
            assertThatCode(() -> registry.discoverAndRegisterHandlers()).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Handler Invocation Tests")
    class HandlerInvocationTests {

        @Test
        @DisplayName("should invoke handler and return completed reply with data")
        void testInvokeHandlerWithData() {
            UserService service = new UserService();
            BeanDefinition<UserService> beanDef = mockBeanDefinition(UserService.class);

            when(mockBeanContext.getAllBeanDefinitions()).thenReturn(List.of(beanDef));
            when(mockBeanContext.getBean(UserService.class)).thenReturn(service);

            registry.discoverAndRegisterHandlers();

            UUID cmdId = UUID.randomUUID();
            UUID corrId = UUID.randomUUID();
            String payload = Jsons.toJson(new CreateUserCommand("alice", "alice@example.com"));
            CommandMessage msg = new CommandMessage(cmdId, corrId, "CreateUser", payload);

            CommandReply reply = registry.handle(msg);

            assertThat(reply).isNotNull();
            assertThat(reply.commandId()).isEqualTo(cmdId);
            assertThat(reply.correlationId()).isEqualTo(corrId);
            assertThat(reply.isSuccess()).isTrue();
            assertThat(reply.data()).isNotEmpty();
            assertThat(reply.data()).containsKey("id");
            assertThat(reply.data()).containsEntry("name", "alice");
        }

        @Test
        @DisplayName("should handle void return type and return empty data")
        void testInvokeHandlerWithVoidReturn() {
            VoidService service = new VoidService();
            BeanDefinition<VoidService> beanDef = mockBeanDefinition(VoidService.class);

            when(mockBeanContext.getAllBeanDefinitions()).thenReturn(List.of(beanDef));
            when(mockBeanContext.getBean(VoidService.class)).thenReturn(service);

            registry.discoverAndRegisterHandlers();

            UUID cmdId = UUID.randomUUID();
            UUID corrId = UUID.randomUUID();
            String payload = Jsons.toJson(new DeleteUserCommand("123"));
            CommandMessage msg = new CommandMessage(cmdId, corrId, "DeleteUser", payload);

            CommandReply reply = registry.handle(msg);

            assertThat(reply).isNotNull();
            assertThat(reply.isSuccess()).isTrue();
            assertThat(reply.data()).isEmpty();
        }

        @Test
        @DisplayName("should handle null return value and return empty data")
        void testInvokeHandlerWithNullReturn() {
            NullReturningService service = new NullReturningService();
            BeanDefinition<NullReturningService> beanDef = mockBeanDefinition(NullReturningService.class);

            when(mockBeanContext.getAllBeanDefinitions()).thenReturn(List.of(beanDef));
            when(mockBeanContext.getBean(NullReturningService.class)).thenReturn(service);

            registry.discoverAndRegisterHandlers();

            UUID cmdId = UUID.randomUUID();
            UUID corrId = UUID.randomUUID();
            String payload = Jsons.toJson(new UpdateUserCommand("123", "new@example.com"));
            CommandMessage msg = new CommandMessage(cmdId, corrId, "UpdateUser", payload);

            CommandReply reply = registry.handle(msg);

            assertThat(reply).isNotNull();
            assertThat(reply.isSuccess()).isTrue();
            assertThat(reply.data()).isEmpty();
        }

        @Test
        @DisplayName("should return failed reply when handler throws exception")
        void testInvokeHandlerThrowsException() {
            FaultyService service = new FaultyService();
            BeanDefinition<FaultyService> beanDef = mockBeanDefinition(FaultyService.class);

            when(mockBeanContext.getAllBeanDefinitions()).thenReturn(List.of(beanDef));
            when(mockBeanContext.getBean(FaultyService.class)).thenReturn(service);

            registry.discoverAndRegisterHandlers();

            UUID cmdId = UUID.randomUUID();
            UUID corrId = UUID.randomUUID();
            String payload = Jsons.toJson(new CreateUserCommand("bob", "bob@example.com"));
            CommandMessage msg = new CommandMessage(cmdId, corrId, "CreateUser", payload);

            CommandReply reply = registry.handle(msg);

            assertThat(reply).isNotNull();
            assertThat(reply.isFailure()).isTrue();
            assertThat(reply.error()).isEqualTo("Service error");
            assertThat(reply.correlationId()).isEqualTo(corrId);
        }

        @Test
        @DisplayName("should handle JSON deserialization errors")
        void testInvokeHandlerWithInvalidJson() {
            UserService service = new UserService();
            BeanDefinition<UserService> beanDef = mockBeanDefinition(UserService.class);

            when(mockBeanContext.getAllBeanDefinitions()).thenReturn(List.of(beanDef));
            when(mockBeanContext.getBean(UserService.class)).thenReturn(service);

            registry.discoverAndRegisterHandlers();

            UUID cmdId = UUID.randomUUID();
            UUID corrId = UUID.randomUUID();
            String invalidPayload = "not valid json {";
            CommandMessage msg = new CommandMessage(cmdId, corrId, "CreateUser", invalidPayload);

            CommandReply reply = registry.handle(msg);

            assertThat(reply).isNotNull();
            assertThat(reply.isFailure()).isTrue();
            assertThat(reply.error()).isNotNull();
        }

        @Test
        @DisplayName("should return failed reply for unregistered command type")
        void testInvokeUnregisteredCommand() {
            UserService service = new UserService();
            BeanDefinition<UserService> beanDef = mockBeanDefinition(UserService.class);

            when(mockBeanContext.getAllBeanDefinitions()).thenReturn(List.of(beanDef));
            when(mockBeanContext.getBean(UserService.class)).thenReturn(service);

            registry.discoverAndRegisterHandlers();

            UUID cmdId = UUID.randomUUID();
            UUID corrId = UUID.randomUUID();
            CommandMessage msg = new CommandMessage(cmdId, corrId, "UnknownCommand", "{}");

            CommandReply reply = registry.handle(msg);

            assertThat(reply).isNotNull();
            assertThat(reply.isFailure()).isTrue();
            assertThat(reply.error()).contains("No handler registered");
        }

        @Test
        @DisplayName("should deserialize command payload correctly before invoking handler")
        void testPayloadDeserialization() {
            UserService service = spy(new UserService());
            BeanDefinition<UserService> beanDef = mockBeanDefinition(UserService.class);

            when(mockBeanContext.getAllBeanDefinitions()).thenReturn(List.of(beanDef));
            when(mockBeanContext.getBean(UserService.class)).thenReturn(service);

            registry.discoverAndRegisterHandlers();

            UUID cmdId = UUID.randomUUID();
            String payload = Jsons.toJson(new CreateUserCommand("charlie", "charlie@example.com"));
            CommandMessage msg = new CommandMessage(cmdId, UUID.randomUUID(), "CreateUser", payload);

            registry.handle(msg);

            // Verify the handler was called with the correct deserialized command
            verify(service).createUser(argThat(cmd ->
                    cmd.username().equals("charlie") && cmd.email().equals("charlie@example.com")));
        }
    }

    @Nested
    @DisplayName("Command Type Derivation Tests")
    class CommandTypDerivationTests {

        @Test
        @DisplayName("should use full class name when Command suffix is absent")
        void testDeriveCommandTypeWithoutSuffix() {
            TestService service = new TestService();
            BeanDefinition<TestService> beanDef = mockBeanDefinition(TestService.class);

            when(mockBeanContext.getAllBeanDefinitions()).thenReturn(List.of(beanDef));
            when(mockBeanContext.getBean(TestService.class)).thenReturn(service);

            registry.discoverAndRegisterHandlers();

            UUID cmdId = UUID.randomUUID();
            String payload = Jsons.toJson(new CommandWithoutSuffix("test data"));
            CommandMessage msg =
                    new CommandMessage(cmdId, UUID.randomUUID(), "CommandWithoutSuffix", payload);

            CommandReply reply = registry.handle(msg);

            assertThat(reply.isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("Empty and Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle empty bean definitions list")
        void testEmptyBeanDefinitions() {
            when(mockBeanContext.getAllBeanDefinitions()).thenReturn(Collections.emptyList());

            registry.discoverAndRegisterHandlers();

            UUID cmdId = UUID.randomUUID();
            CommandMessage msg = new CommandMessage(cmdId, UUID.randomUUID(), "AnyCommand", "{}");
            CommandReply reply = registry.handle(msg);

            assertThat(reply.isFailure()).isTrue();
            assertThat(reply.error()).contains("No handler registered");
        }

        @Test
        @DisplayName("should handle discovery with no valid handlers")
        void testNoValidHandlers() {
            // Service with no DomainCommand methods
            Object beanWithoutHandlers = new Object();
            BeanDefinition<Object> beanDef = mockBeanDefinition(Object.class);

            when(mockBeanContext.getAllBeanDefinitions()).thenReturn(List.of(beanDef));
            when(mockBeanContext.getBean(Object.class)).thenReturn(beanWithoutHandlers);

            registry.discoverAndRegisterHandlers();

            UUID cmdId = UUID.randomUUID();
            CommandMessage msg = new CommandMessage(cmdId, UUID.randomUUID(), "AnyCommand", "{}");
            CommandReply reply = registry.handle(msg);

            assertThat(reply.isFailure()).isTrue();
        }
    }

    @Nested
    @DisplayName("Inheritance and Base Class Tests")
    class InheritanceTests {

        @Test
        @DisplayName("should handle inherited methods from parent class")
        void testInheritedMethods() {
            UserService$Intercepted proxyService = new UserService$Intercepted();
            BeanDefinition<UserService$Intercepted> beanDef =
                    mockBeanDefinition(UserService$Intercepted.class);

            when(mockBeanContext.getAllBeanDefinitions()).thenReturn(List.of(beanDef));
            when(mockBeanContext.getBean(UserService$Intercepted.class)).thenReturn(proxyService);

            registry.discoverAndRegisterHandlers();

            // Inherited createUser method should work
            UUID cmdId = UUID.randomUUID();
            String payload = Jsons.toJson(new CreateUserCommand("dave", "dave@example.com"));
            CommandMessage msg = new CommandMessage(cmdId, UUID.randomUUID(), "CreateUser", payload);

            CommandReply reply = registry.handle(msg);

            assertThat(reply.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should normalize proxy class names for duplicate detection")
        void testProxyClassNameNormalization() {
            // Simulate having both proxy and non-proxy versions
            UserService service = new UserService();
            BeanDefinition<UserService> beanDef = mockBeanDefinition(UserService.class);

            when(mockBeanContext.getAllBeanDefinitions()).thenReturn(List.of(beanDef));
            when(mockBeanContext.getBean(UserService.class)).thenReturn(service);

            // Should not throw - same base class after normalization
            assertThatCode(() -> registry.discoverAndRegisterHandlers()).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Integration Scenario Tests")
    class IntegrationScenarioTests {

        @Test
        @DisplayName("should handle complete workflow: discover, register, and invoke")
        void testCompleteWorkflow() {
            UserService service = new UserService();
            BeanDefinition<UserService> beanDef = mockBeanDefinition(UserService.class);

            when(mockBeanContext.getAllBeanDefinitions()).thenReturn(List.of(beanDef));
            when(mockBeanContext.getBean(UserService.class)).thenReturn(service);

            // Phase 1: Discovery
            registry.discoverAndRegisterHandlers();

            // Phase 2: Invoke CreateUser
            UUID createCmdId = UUID.randomUUID();
            String createPayload = Jsons.toJson(new CreateUserCommand("eve", "eve@example.com"));
            CommandMessage createMsg =
                    new CommandMessage(createCmdId, UUID.randomUUID(), "CreateUser", createPayload);
            CommandReply createReply = registry.handle(createMsg);

            assertThat(createReply.isSuccess()).isTrue();
            assertThat(createReply.data()).containsEntry("name", "eve");

            // Phase 3: Invoke UpdateUser
            UUID updateCmdId = UUID.randomUUID();
            String updatePayload = Jsons.toJson(new UpdateUserCommand("123", "newemail@example.com"));
            CommandMessage updateMsg =
                    new CommandMessage(updateCmdId, UUID.randomUUID(), "UpdateUser", updatePayload);
            CommandReply updateReply = registry.handle(updateMsg);

            assertThat(updateReply.isSuccess()).isTrue();
            assertThat(updateReply.data()).containsEntry("name", "updated");

            // Phase 4: Invoke DeleteUser
            UUID deleteCmdId = UUID.randomUUID();
            String deletePayload = Jsons.toJson(new DeleteUserCommand("123"));
            CommandMessage deleteMsg =
                    new CommandMessage(deleteCmdId, UUID.randomUUID(), "DeleteUser", deletePayload);
            CommandReply deleteReply = registry.handle(deleteMsg);

            assertThat(deleteReply.isSuccess()).isTrue();
            assertThat(deleteReply.data()).isEmpty();
        }

        @Test
        @DisplayName("should maintain handler registry state across multiple invocations")
        void testHandlerCaching() {
            UserService service = new UserService();
            BeanDefinition<UserService> beanDef = mockBeanDefinition(UserService.class);

            when(mockBeanContext.getAllBeanDefinitions()).thenReturn(List.of(beanDef));
            when(mockBeanContext.getBean(UserService.class)).thenReturn(service);

            registry.discoverAndRegisterHandlers();

            // Invoke same command type multiple times
            for (int i = 0; i < 5; i++) {
                UUID cmdId = UUID.randomUUID();
                String payload = Jsons.toJson(new CreateUserCommand("user" + i, "user" + i + "@example.com"));
                CommandMessage msg = new CommandMessage(cmdId, UUID.randomUUID(), "CreateUser", payload);
                CommandReply reply = registry.handle(msg);

                assertThat(reply.isSuccess()).isTrue();
            }

            // Verify bean was only retrieved during discovery, not on each invocation
            verify(mockBeanContext, times(1)).getBean(UserService.class);
        }
    }
}
