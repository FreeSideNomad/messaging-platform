package com.acme.reliable.command;

import com.acme.reliable.process.CommandReply;
import io.micronaut.context.BeanContext;
import io.micronaut.inject.BeanDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for AutoCommandHandlerRegistry auto-discovery mechanism
 */
class AutoCommandHandlerRegistryTest {

    private BeanContext mockBeanContext;
    private AutoCommandHandlerRegistry registry;

    // Test command
    public record TestCommand(String value) implements DomainCommand {}
    public record AnotherTestCommand(String data) implements DomainCommand {}

    // Test service with command handler method
    public static class TestService {
        public String handleTest(TestCommand command) {
            return "Handled: " + command.value();
        }

        public String handleAnother(AnotherTestCommand command) {
            return "Processed: " + command.data();
        }
    }

    // Service with void return
    public static class VoidService {
        public void handleTest(TestCommand command) {
            // void operation
        }
    }

    // Service with multiple handlers for same command (should fail)
    public static class DuplicateService1 {
        public String handleTest(TestCommand command) {
            return "First handler";
        }
    }

    public static class DuplicateService2 {
        public String handleTest(TestCommand command) {
            return "Second handler";
        }
    }

    @BeforeEach
    void setup() {
        mockBeanContext = mock(BeanContext.class);
        registry = new AutoCommandHandlerRegistry(mockBeanContext);
    }

    @Test
    void testAutoDiscovery_SingleHandler() {
        // Given: A service with a command handler method
        TestService service = new TestService();
        mockBeanDefinitions(service);

        // When: Auto-discovery runs
        registry.discoverAndRegisterHandlers();

        // Then: Handler should be registered and work correctly
        CommandMessage command = new CommandMessage(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Test",
            "{\"value\":\"test-data\"}"
        );

        CommandReply reply = registry.handle(command);

        assertEquals(CommandReply.ReplyStatus.COMPLETED, reply.status());
        assertEquals("Handled: test-data", reply.data().get("data"));
    }

    @Test
    void testAutoDiscovery_MultipleHandlers() {
        // Given: A service with multiple command handler methods
        TestService service = new TestService();
        mockBeanDefinitions(service);

        // When: Auto-discovery runs
        registry.discoverAndRegisterHandlers();

        // Then: All handlers should be registered
        CommandMessage testCmd = new CommandMessage(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Test",
            "{\"value\":\"test\"}"
        );

        CommandMessage anotherCmd = new CommandMessage(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "AnotherTest",
            "{\"data\":\"another\"}"
        );

        CommandReply reply1 = registry.handle(testCmd);
        CommandReply reply2 = registry.handle(anotherCmd);

        assertEquals(CommandReply.ReplyStatus.COMPLETED, reply1.status());
        assertEquals(CommandReply.ReplyStatus.COMPLETED, reply2.status());
        assertEquals("Handled: test", reply1.data().get("data"));
        assertEquals("Processed: another", reply2.data().get("data"));
    }

    @Test
    void testAutoDiscovery_VoidHandler() {
        // Given: A service with void return handler
        VoidService service = new VoidService();
        mockBeanDefinitions(service);

        // When: Auto-discovery runs
        registry.discoverAndRegisterHandlers();

        // Then: Handler should work with empty data map
        CommandMessage command = new CommandMessage(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Test",
            "{\"value\":\"test\"}"
        );

        CommandReply reply = registry.handle(command);

        assertEquals(CommandReply.ReplyStatus.COMPLETED, reply.status());
        assertTrue(reply.data().isEmpty());
    }

    @Test
    void testAutoDiscovery_AmbiguousHandlers() {
        // Given: Two services with handlers for same command type
        DuplicateService1 service1 = new DuplicateService1();
        DuplicateService2 service2 = new DuplicateService2();

        // Mock bean definitions for both services
        BeanDefinition<?> beanDef1 = mockBeanDefinition(service1);
        BeanDefinition<?> beanDef2 = mockBeanDefinition(service2);

        when(mockBeanContext.getAllBeanDefinitions())
            .thenReturn(Arrays.asList(beanDef1, beanDef2));

        when(mockBeanContext.getBean(DuplicateService1.class)).thenReturn(service1);
        when(mockBeanContext.getBean(DuplicateService2.class)).thenReturn(service2);

        // When/Then: Should throw IllegalStateException
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> registry.discoverAndRegisterHandlers()
        );

        assertTrue(exception.getMessage().contains("Ambiguous handler registration"));
        assertTrue(exception.getMessage().contains("Test"));
    }

    @Test
    void testDeriveCommandType_RemovesCommandSuffix() {
        // Given: A command with "Command" suffix
        TestService service = new TestService();
        mockBeanDefinitions(service);

        registry.discoverAndRegisterHandlers();

        // When: Handling a command
        CommandMessage command = new CommandMessage(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Test",  // "TestCommand" -> "Test"
            "{\"value\":\"data\"}"
        );

        // Then: Should handle correctly
        CommandReply reply = registry.handle(command);
        assertEquals(CommandReply.ReplyStatus.COMPLETED, reply.status());
    }

    @Test
    void testHandleError_ReturnsFailedReply() {
        // Given: A service that throws exception
        TestService service = new TestService() {
            @Override
            public String handleTest(TestCommand command) {
                throw new RuntimeException("Test error");
            }
        };
        mockBeanDefinitions(service);

        registry.discoverAndRegisterHandlers();

        // When: Handling command that throws exception
        CommandMessage command = new CommandMessage(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Test",
            "{\"value\":\"test\"}"
        );

        CommandReply reply = registry.handle(command);

        // Then: Should return failed reply
        assertEquals(CommandReply.ReplyStatus.FAILED, reply.status());
        assertTrue(reply.error().contains("Test error"));
    }

    // Helper methods
    @SuppressWarnings("unchecked")
    private void mockBeanDefinitions(Object... beans) {
        List<BeanDefinition<?>> beanDefinitions = new ArrayList<>();

        for (Object bean : beans) {
            BeanDefinition<?> beanDef = mockBeanDefinition(bean);
            beanDefinitions.add(beanDef);
            when(mockBeanContext.getBean((Class) bean.getClass())).thenReturn(bean);
        }

        when(mockBeanContext.getAllBeanDefinitions()).thenReturn(beanDefinitions);
    }

    @SuppressWarnings("unchecked")
    private BeanDefinition<?> mockBeanDefinition(Object bean) {
        BeanDefinition<?> beanDef = mock(BeanDefinition.class);
        when(beanDef.getBeanType()).thenReturn((Class) bean.getClass());
        return beanDef;
    }
}
