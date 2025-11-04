package com.acme.reliable.processor.process;

import com.acme.reliable.command.CommandBus;
import com.acme.reliable.process.BaseProcessManager;
import com.acme.reliable.process.ProcessConfiguration;
import com.acme.reliable.repository.ProcessRepository;
import io.micronaut.context.BeanContext;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Micronaut-specific Process Manager implementation.
 * Handles infrastructure concerns: transactions, dependency injection, and lifecycle.
 * Domain logic is in BaseProcessManager.
 */
@Singleton
public class ProcessManager extends BaseProcessManager implements ApplicationEventListener<ServerStartupEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessManager.class);

    private final ProcessRepository processRepo;
    private final CommandBus commandBus;
    private final BeanContext beanContext;

    public ProcessManager(ProcessRepository processRepo, CommandBus commandBus, BeanContext beanContext) {
        this.processRepo = processRepo;
        this.commandBus = commandBus;
        this.beanContext = beanContext;
    }

    @Override
    protected ProcessRepository getProcessRepository() {
        return processRepo;
    }

    @Override
    protected CommandBus getCommandBus() {
        return commandBus;
    }

    @Override
    @Transactional
    protected void executeInTransaction(Runnable action) {
        action.run();
    }

    /**
     * Auto-discover and register all ProcessConfiguration beans on startup
     */
    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
        LOG.info("Auto-discovering process configurations...");
        int configurationsRegistered = 0;

        for (ProcessConfiguration config : beanContext.getBeansOfType(ProcessConfiguration.class)) {
            try {
                register(config);
                configurationsRegistered++;
                LOG.info("Auto-registered process: {}", config.getProcessType());
            } catch (IllegalStateException e) {
                String errorMsg = String.format(
                    "Ambiguous process configuration for type '%s': %s",
                    config.getProcessType(),
                    e.getMessage()
                );
                LOG.error(errorMsg);
                throw new IllegalStateException(errorMsg, e);
            }
        }

        LOG.info("Auto-discovery complete: {} process(es) registered", configurationsRegistered);
    }
}
