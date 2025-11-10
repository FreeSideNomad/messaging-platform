package com.acme.reliable.processor.command;

import com.acme.reliable.command.CommandHandlerRegistry;
import com.acme.reliable.command.DomainCommand;
import com.acme.reliable.core.Jsons;
import com.acme.reliable.process.CommandReply;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.inject.BeanDefinition;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * Auto-discovers and registers command handlers using reflection and convention.
 *
 * <p>Convention: - Scans all beans for methods with a single parameter implementing DomainCommand -
 * Derives command type from command class name by removing "Command" suffix - Automatically wraps
 * service method calls in CommandReply - Throws IllegalStateException if multiple methods handle
 * the same command type (ambiguous registration)
 *
 * <p>Example:
 *
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
@Requires(notEnv = "test")
@RequiredArgsConstructor
@Slf4j
public class AutoCommandHandlerRegistry extends CommandHandlerRegistry {
    private final BeanContext beanContext;

    @PostConstruct
    public void discoverAndRegisterHandlers() {
        log.info("Auto-discovering command handlers...");

        // Phase 1: Collect all candidates (including duplicates from proxies)
        Map<String, List<HandlerCandidate>> candidatesByCommandType = collectCandidates();

        // Phase 2: Consolidate and register one handler per command type
        int handlersRegistered = consolidateAndRegister(candidatesByCommandType);

        log.info("Auto-discovery complete: {} handler(s) registered", handlersRegistered);
    }

    /**
     * Collect all handler candidates from bean definitions
     */
    private Map<String, List<HandlerCandidate>> collectCandidates() {
        Map<String, List<HandlerCandidate>> candidatesByCommandType = new HashMap<>();

        for (BeanDefinition<?> beanDefinition : beanContext.getAllBeanDefinitions()) {
            Class<?> beanClass = beanDefinition.getBeanType();

            // Skip our own registry classes
            if (CommandHandlerRegistry.class.isAssignableFrom(beanClass)) {
                continue;
            }

            // Get the actual bean instance - skip if not available yet
            Optional<Object> beanOpt = getBeanSafely(beanClass);
            if (beanOpt.isEmpty()) {
                continue;
            }

            Object bean = beanOpt.get();
            scanBeanForHandlers(bean, candidatesByCommandType);
        }

        return candidatesByCommandType;
    }

    /**
     * Safely get a bean, returning empty if not available
     */
    private Optional<Object> getBeanSafely(Class<?> beanClass) {
        try {
            return Optional.of(beanContext.getBean(beanClass));
        } catch (Exception e) {
            // Skip beans that aren't available yet during initialization
            log.debug(
                    "Skipping bean {} - not yet available: {}", beanClass.getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Scan a bean for handler methods
     */
    private void scanBeanForHandlers(
            Object bean, Map<String, List<HandlerCandidate>> candidatesByCommandType) {
        Class<?> actualBeanClass = bean.getClass();
        boolean isProxy =
                actualBeanClass.getName().contains("$Intercepted")
                        || actualBeanClass.getName().contains("$Proxy");

        for (Method method : actualBeanClass.getMethods()) {
            // Skip Micronaut AOP accessor methods
            if (method.getName().contains("$$access$$")) {
                continue;
            }

            processMethodIfHandler(method, bean, isProxy, actualBeanClass, candidatesByCommandType);
        }
    }

    /**
     * Process a method if it's a command handler
     */
    private void processMethodIfHandler(
            Method method,
            Object bean,
            boolean isProxy,
            Class<?> actualBeanClass,
            Map<String, List<HandlerCandidate>> candidatesByCommandType) {
        if (method.getParameterCount() != 1) {
            return;
        }

        Parameter param = method.getParameters()[0];
        Class<?> paramType = param.getType();

        if (!DomainCommand.class.isAssignableFrom(paramType)) {
            return;
        }

        // Derive command type from class name
        String commandType = deriveCommandType(paramType);

        // Add to candidates list
        HandlerCandidate candidate =
                new HandlerCandidate(
                        commandType, bean, method, paramType, isProxy, actualBeanClass.getSimpleName());

        candidatesByCommandType.computeIfAbsent(commandType, k -> new ArrayList<>()).add(candidate);

        log.debug(
                "Found candidate handler: {} -> {}.{}({}) [proxy={}]",
                commandType,
                actualBeanClass.getSimpleName(),
                method.getName(),
                paramType.getSimpleName(),
                isProxy);
    }

    /**
     * Consolidate candidates and register handlers
     */
    private int consolidateAndRegister(Map<String, List<HandlerCandidate>> candidatesByCommandType) {
        int handlersRegistered = 0;

        for (Map.Entry<String, List<HandlerCandidate>> entry : candidatesByCommandType.entrySet()) {
            String commandType = entry.getKey();
            List<HandlerCandidate> candidates = entry.getValue();

            if (candidates.isEmpty()) {
                continue;
            }

            // Select the best candidate: prefer proxies, then first available
            HandlerCandidate selected = selectBestCandidate(candidates);

            // Validate no ambiguous implementations
            validateUniqueImplementation(commandType, candidates);

            // Register the selected handler
            registerAutoHandler(
                    selected.commandType, selected.bean, selected.method, selected.commandClass);
            handlersRegistered++;

            log.info(
                    "Auto-registered handler: {} -> {}.{}({}) [proxy={}, candidates={}]",
                    selected.commandType,
                    selected.beanClassName,
                    selected.method.getName(),
                    selected.commandClass.getSimpleName(),
                    selected.isProxy,
                    candidates.size());
        }

        return handlersRegistered;
    }

    /**
     * Select the best candidate from the list (prefer proxies)
     */
    private HandlerCandidate selectBestCandidate(List<HandlerCandidate> candidates) {
        return candidates.stream().filter(c -> c.isProxy).findFirst().orElse(candidates.get(0));
    }

    /**
     * Validate that there's only one unique implementation for a command type
     */
    private void validateUniqueImplementation(String commandType, List<HandlerCandidate> candidates) {
        Set<String> uniqueBaseClasses = new HashSet<>();
        for (HandlerCandidate c : candidates) {
            String baseClassName =
                    c.beanClassName.replaceAll("\\$Intercepted.*", "").replaceAll("\\$Proxy.*", "");
            uniqueBaseClasses.add(baseClassName);
        }

        if (uniqueBaseClasses.size() > 1) {
            String errorMsg =
                    String.format(
                            "Ambiguous handler registration for command type '%s': "
                                    + "Found %d different implementations: %s. "
                                    + "Only one implementation per command type is allowed.",
                            commandType, uniqueBaseClasses.size(), uniqueBaseClasses);
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
    }

    /**
     * Derive command type from command class name. Example: CreateAccountCommand -> CreateAccount
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
    private void registerAutoHandler(
            String commandType, Object bean, Method method, Class<?> commandClass) {
        registerHandler(
                commandType,
                command -> {
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

                        return CommandReply.completed(command.commandId(), command.correlationId(), replyData);

                    } catch (Exception e) {
                        log.error("Error executing handler for {}", commandType, e);

                        // Unwrap reflection exceptions
                        Throwable cause = e.getCause() != null ? e.getCause() : e;

                        return CommandReply.failed(
                                UUID.randomUUID(), command.correlationId(), cause.getMessage());
                    }
                });
    }

    /**
     * Holds a candidate handler method with its bean and metadata
     */
    private static class HandlerCandidate {
        final String commandType;
        final Object bean;
        final Method method;
        final Class<?> commandClass;
        final boolean isProxy;
        final String beanClassName;

        HandlerCandidate(
                String commandType,
                Object bean,
                Method method,
                Class<?> commandClass,
                boolean isProxy,
                String beanClassName) {
            this.commandType = commandType;
            this.bean = bean;
            this.method = method;
            this.commandClass = commandClass;
            this.isProxy = isProxy;
            this.beanClassName = beanClassName;
        }
    }
}
