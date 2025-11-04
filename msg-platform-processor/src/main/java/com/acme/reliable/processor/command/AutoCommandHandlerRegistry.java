package com.acme.reliable.processor.command;

import com.acme.reliable.command.CommandHandlerRegistry;
import com.acme.reliable.command.DomainCommand;
import com.acme.reliable.core.Jsons;
import com.acme.reliable.process.CommandReply;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.inject.BeanDefinition;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.UUID;

/**
 * Auto-discovers and registers command handlers using reflection and convention.
 *
 * Convention:
 * - Scans all beans for methods with a single parameter implementing DomainCommand
 * - Derives command type from command class name by removing "Command" suffix
 * - Automatically wraps service method calls in CommandReply
 * - Throws IllegalStateException if multiple methods handle the same command type (ambiguous registration)
 *
 * Example:
 * <pre>
 * public record CreateAccountCommand(...) implements DomainCommand {}
 *
 * {@literal @}Singleton
 * public class AccountService {
 *     public Account createAccount(CreateAccountCommand cmd) {
 *         // Auto-discovered and registered as handler for "CreateAccount"
 *     }
 * }
 * </pre>
 */
@Context
@RequiredArgsConstructor
@Slf4j
public class AutoCommandHandlerRegistry extends CommandHandlerRegistry {
    private final BeanContext beanContext;

    @PostConstruct
    public void discoverAndRegisterHandlers() {
        log.info("Auto-discovering command handlers...");
        int handlersRegistered = 0;

        // Scan all bean definitions
        for (BeanDefinition<?> beanDefinition : beanContext.getAllBeanDefinitions()) {
            Class<?> beanClass = beanDefinition.getBeanType();

            // Skip our own registry classes
            if (CommandHandlerRegistry.class.isAssignableFrom(beanClass)) {
                continue;
            }

            // Get the actual bean instance
            Object bean = beanContext.getBean(beanClass);

            // Scan all methods in the bean
            for (Method method : beanClass.getMethods()) {
                // Check if method has exactly one parameter that implements DomainCommand
                if (method.getParameterCount() == 1) {
                    Parameter param = method.getParameters()[0];
                    Class<?> paramType = param.getType();

                    if (DomainCommand.class.isAssignableFrom(paramType)) {
                        // Derive command type from class name
                        String commandType = deriveCommandType(paramType);

                        // Register handler with detailed error on conflict
                        try {
                            registerAutoHandler(commandType, bean, method, paramType);
                            handlersRegistered++;

                            log.info("Auto-registered handler: {} -> {}.{}({})",
                                commandType,
                                beanClass.getSimpleName(),
                                method.getName(),
                                paramType.getSimpleName());
                        } catch (IllegalStateException e) {
                            String errorMsg = String.format(
                                "Ambiguous handler registration for command type '%s': " +
                                "Cannot register %s.%s(%s) - a handler is already registered. " +
                                "Only one method per command type is allowed.",
                                commandType,
                                beanClass.getName(),
                                method.getName(),
                                paramType.getSimpleName()
                            );
                            log.error(errorMsg);
                            throw new IllegalStateException(errorMsg, e);
                        }
                    }
                }
            }
        }

        log.info("Auto-discovery complete: {} handler(s) registered", handlersRegistered);
    }

    /**
     * Derive command type from command class name.
     * Example: CreateAccountCommand -> CreateAccount
     */
    private String deriveCommandType(Class<?> commandClass) {
        String className = commandClass.getSimpleName();
        if (className.endsWith("Command")) {
            return className.substring(0, className.length() - "Command".length());
        }
        return className;
    }

    /**
     * Register a handler that invokes a service method via reflection
     */
    private void registerAutoHandler(String commandType, Object bean, Method method, Class<?> commandClass) {
        registerHandler(commandType, command -> {
            try {
                // Deserialize command payload to the specific command type
                Object commandObj = Jsons.fromJson(command.payload(), commandClass);

                // Invoke the service method
                Object result = method.invoke(bean, commandObj);

                // Build reply based on return type
                Map<String, Object> replyData;
                if (method.getReturnType() == void.class || method.getReturnType() == Void.class) {
                    replyData = Map.of();
                } else if (result != null) {
                    replyData = Jsons.toMap(result);
                } else {
                    replyData = Map.of();
                }

                return CommandReply.completed(
                    command.commandId(),
                    command.correlationId(),
                    replyData
                );

            } catch (Exception e) {
                log.error("Error executing handler for {}", commandType, e);

                // Unwrap reflection exceptions
                Throwable cause = e.getCause() != null ? e.getCause() : e;

                return CommandReply.failed(
                    UUID.randomUUID(),
                    command.correlationId(),
                    cause.getMessage()
                );
            }
        });
    }
}
