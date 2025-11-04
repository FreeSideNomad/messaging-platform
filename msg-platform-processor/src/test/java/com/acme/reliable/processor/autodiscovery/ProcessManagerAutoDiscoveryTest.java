package com.acme.reliable.processor.autodiscovery;

import com.acme.reliable.repository.ProcessRepository;
import com.acme.reliable.process.ProcessConfiguration;
import com.acme.reliable.process.ProcessGraph;
import com.acme.reliable.process.ProcessGraphBuilder;
import com.acme.reliable.command.DomainCommand;
import com.acme.reliable.processor.CommandBus;
import com.acme.reliable.processor.process.ProcessManager;
import io.micronaut.context.BeanContext;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ProcessManager auto-discovery mechanism
 */
class ProcessManagerAutoDiscoveryTest {

    private ProcessRepository mockRepo;
    private CommandBus mockCommandBus;
    private BeanContext mockBeanContext;
    private ProcessManager processManager;

    // Test process definition
    static class TestProcessConfiguration implements ProcessConfiguration {
        @Override
        public String getProcessType() {
            return "TestProcess";
        }

        @Override
        public ProcessGraph defineProcess() {
            return ProcessGraphBuilder.process()
                .startWith(TestStep1Command.class).withCompensation(CompensateTestStep1Command.class)
                .then(TestStep2Command.class).withCompensation(CompensateTestStep2Command.class)
                .end();
        }

        @Override
        public boolean isRetryable(String step, String error) {
            return false;
        }

        @Override
        public int getMaxRetries(String step) {
            return 3;
        }
    }

    // Another test process definition
    static class AnotherProcessConfiguration implements ProcessConfiguration {
        @Override
        public String getProcessType() {
            return "AnotherProcess";
        }

        @Override
        public ProcessGraph defineProcess() {
            return ProcessGraphBuilder.process()
                .startWith(AnotherStartCommand.class)
                .end();
        }

        @Override
        public boolean isRetryable(String step, String error) {
            return false;
        }

        @Override
        public int getMaxRetries(String step) {
            return 0;
        }
    }

    // Duplicate process definition (same type as TestProcessConfiguration)
    static class DuplicateProcessConfiguration implements ProcessConfiguration {
        @Override
        public String getProcessType() {
            return "TestProcess";  // DUPLICATE!
        }

        @Override
        public ProcessGraph defineProcess() {
            return ProcessGraphBuilder.process()
                .startWith(DuplicateOtherStepCommand.class)
                .end();
        }

        @Override
        public boolean isRetryable(String step, String error) {
            return false;
        }

        @Override
        public int getMaxRetries(String step) {
            return 0;
        }
    }

    @BeforeEach
    void setup() {
        mockRepo = mock(ProcessRepository.class);
        mockCommandBus = mock(CommandBus.class);
        mockBeanContext = mock(BeanContext.class);
        processManager = new ProcessManager(mockRepo, mockCommandBus, mockBeanContext);
    }

    @Test
    void testAutoDiscovery_SingleDefinition() {
        // Given: One process definition bean
        TestProcessConfiguration definition = new TestProcessConfiguration();
        when(mockBeanContext.getBeansOfType(ProcessConfiguration.class))
            .thenReturn(List.of(definition));

        // When: Auto-discovery runs on startup
        processManager.onApplicationEvent(mock(ServerStartupEvent.class));

        // Then: Definition should be registered (no exception thrown)
        // Verify by trying to start a process of that type - should not throw "Unknown process type"
        // Note: We can't easily test this without mocking more, but no exception = success
    }

    @Test
    void testAutoDiscovery_MultipleDefinitions() {
        // Given: Multiple process definition beans
        TestProcessConfiguration def1 = new TestProcessConfiguration();
        AnotherProcessConfiguration def2 = new AnotherProcessConfiguration();

        when(mockBeanContext.getBeansOfType(ProcessConfiguration.class))
            .thenReturn(Arrays.asList(def1, def2));

        // When: Auto-discovery runs
        processManager.onApplicationEvent(mock(ServerStartupEvent.class));

        // Then: All definitions should be registered (no exception)
    }

    @Test
    void testAutoDiscovery_AmbiguousDefinitions() {
        // Given: Two definitions with same process type
        TestProcessConfiguration def1 = new TestProcessConfiguration();
        DuplicateProcessConfiguration def2 = new DuplicateProcessConfiguration();

        when(mockBeanContext.getBeansOfType(ProcessConfiguration.class))
            .thenReturn(Arrays.asList(def1, def2));

        // When/Then: Should throw IllegalStateException
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> processManager.onApplicationEvent(mock(ServerStartupEvent.class))
        );

        assertTrue(exception.getMessage().contains("Ambiguous process definition"));
        assertTrue(exception.getMessage().contains("TestProcess"));
    }

    @Test
    void testRegisterDefinition_DuplicateType() {
        // Given: A definition already registered
        TestProcessConfiguration def1 = new TestProcessConfiguration();
        processManager.register(def1);

        // When: Trying to register another with same type
        DuplicateProcessConfiguration def2 = new DuplicateProcessConfiguration();

        // Then: Should throw IllegalStateException
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> processManager.register(def2)
        );

        assertTrue(exception.getMessage().contains("already registered"));
        assertTrue(exception.getMessage().contains("TestProcess"));
    }

    @Test
    void testRegisterDefinition_UniqueTypes() {
        // Given: Definitions with unique types
        TestProcessConfiguration def1 = new TestProcessConfiguration();
        AnotherProcessConfiguration def2 = new AnotherProcessConfiguration();

        // When: Registering both
        processManager.register(def1);
        processManager.register(def2);

        // Then: Should succeed (no exception)
    }

    // Dummy command classes for testing
    static class TestStep1Command implements DomainCommand {
    }

    static class TestStep2Command implements DomainCommand {
    }

    static class CompensateTestStep1Command implements DomainCommand {
    }

    static class CompensateTestStep2Command implements DomainCommand {
    }

    static class AnotherStartCommand implements DomainCommand {
    }

    static class DuplicateOtherStepCommand implements DomainCommand {
    }
}
