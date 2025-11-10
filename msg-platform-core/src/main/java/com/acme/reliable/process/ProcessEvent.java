package com.acme.reliable.process;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Map;

/**
 * Sealed interface representing events in a process lifecycle. All events are immutable and used
 * for event sourcing the process log.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ProcessEvent.ProcessStarted.class, name = "ProcessStarted"),
        @JsonSubTypes.Type(value = ProcessEvent.StepStarted.class, name = "StepStarted"),
        @JsonSubTypes.Type(value = ProcessEvent.StepCompleted.class, name = "StepCompleted"),
        @JsonSubTypes.Type(value = ProcessEvent.StepFailed.class, name = "StepFailed"),
        @JsonSubTypes.Type(value = ProcessEvent.StepTimedOut.class, name = "StepTimedOut"),
        @JsonSubTypes.Type(value = ProcessEvent.CompensationStarted.class, name = "CompensationStarted"),
        @JsonSubTypes.Type(
                value = ProcessEvent.CompensationCompleted.class,
                name = "CompensationCompleted"),
        @JsonSubTypes.Type(value = ProcessEvent.CompensationFailed.class, name = "CompensationFailed"),
        @JsonSubTypes.Type(value = ProcessEvent.ProcessCompleted.class, name = "ProcessCompleted"),
        @JsonSubTypes.Type(value = ProcessEvent.ProcessFailed.class, name = "ProcessFailed"),
        @JsonSubTypes.Type(value = ProcessEvent.ProcessPaused.class, name = "ProcessPaused"),
        @JsonSubTypes.Type(value = ProcessEvent.ProcessResumed.class, name = "ProcessResumed")
})
public sealed interface ProcessEvent {

    /**
     * Process instance created and started
     */
    record ProcessStarted(String processType, String businessKey, Map<String, Object> initialData)
            implements ProcessEvent {
    }

    /**
     * A process step has been initiated
     */
    record StepStarted(String step, String commandId) implements ProcessEvent {
    }

    /**
     * A process step completed successfully
     */
    record StepCompleted(String step, String commandId, Map<String, Object> resultData)
            implements ProcessEvent {
    }

    /**
     * A process step failed
     */
    record StepFailed(String step, String commandId, String error, boolean retryable)
            implements ProcessEvent {
    }

    /**
     * A process step timed out
     */
    record StepTimedOut(String step, String commandId, String error) implements ProcessEvent {
    }

    /**
     * Compensation step started
     */
    record CompensationStarted(String step, String commandId) implements ProcessEvent {
    }

    /**
     * Compensation step completed
     */
    record CompensationCompleted(String step, String commandId) implements ProcessEvent {
    }

    /**
     * Compensation step failed
     */
    record CompensationFailed(String step, String commandId, String error) implements ProcessEvent {
    }

    /**
     * Process completed successfully
     */
    record ProcessCompleted(String reason) implements ProcessEvent {
    }

    /**
     * Process failed permanently
     */
    record ProcessFailed(String reason) implements ProcessEvent {
    }

    /**
     * Process paused by operator
     */
    record ProcessPaused(String reason, String pausedBy) implements ProcessEvent {
    }

    /**
     * Process resumed by operator
     */
    record ProcessResumed(String reason, String resumedBy) implements ProcessEvent {
    }
}
