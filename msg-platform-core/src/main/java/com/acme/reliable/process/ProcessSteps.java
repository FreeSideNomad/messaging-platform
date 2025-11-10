package com.acme.reliable.process;

import com.acme.reliable.command.DomainCommand;

import java.util.Optional;

/**
 * Utility for deriving type-safe step names from DomainCommand classes. Ensures consistency between
 * command types and process steps.
 */
public final class ProcessSteps {

    private ProcessSteps() {
        // Utility class
    }

    /**
     * Derive step name from command class by removing "Command" suffix. This matches the convention
     * used by AutoCommandHandlerRegistry.
     *
     * <p>Example: BookLimitsCommand.class -> "BookLimits"
     *
     * @param commandClass the command class
     * @return the step name derived from the command class
     */
    public static String stepName(Class<? extends DomainCommand> commandClass) {
        String name = commandClass.getSimpleName();
        return name.endsWith("Command") ? name.substring(0, name.length() - 7) : name;
    }

    /**
     * Derive optional step name from command class. Convenience method that wraps stepName() in
     * Optional.of().
     *
     * <p>Example: optionalStepName(BookLimitsCommand.class) -> Optional.of("BookLimits")
     *
     * @param commandClass the command class
     * @return the step name wrapped in Optional
     */
    public static Optional<String> optionalStepName(Class<? extends DomainCommand> commandClass) {
        return Optional.of(stepName(commandClass));
    }

    /**
     * Check if the current step matches the given command class.
     *
     * <p>Example: is(currentStep, BookLimitsCommand.class)
     *
     * @param currentStep  the current step name
     * @param commandClass the command class to check against
     * @return true if the current step matches the command class
     */
    public static boolean is(String currentStep, Class<? extends DomainCommand> commandClass) {
        return currentStep.equals(stepName(commandClass));
    }

    /**
     * Check if the current step matches any of the given command classes.
     *
     * <p>Example: isOneOf(currentStep, BookLimitsCommand.class, BookFxCommand.class)
     *
     * @param currentStep    the current step name
     * @param commandClasses the command classes to check against
     * @return true if the current step matches any of the command classes
     */
    @SafeVarargs
    public static boolean isOneOf(
            String currentStep, Class<? extends DomainCommand>... commandClasses) {
        for (Class<? extends DomainCommand> commandClass : commandClasses) {
            if (is(currentStep, commandClass)) {
                return true;
            }
        }
        return false;
    }
}
