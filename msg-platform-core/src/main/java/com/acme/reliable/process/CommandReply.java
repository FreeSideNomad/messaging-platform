package com.acme.reliable.process;

import com.acme.reliable.core.Jsons;

import java.util.Map;
import java.util.UUID;

/**
 * Represents a reply from a command execution
 */
public record CommandReply(
    UUID commandId,
    UUID correlationId,
    ReplyStatus status,
    Map<String, Object> data,
    String error
) {
    public enum ReplyStatus {
        COMPLETED,
        FAILED,
        TIMED_OUT
    }

    public static CommandReply completed(UUID commandId, UUID correlationId, Map<String, Object> data) {
        return new CommandReply(commandId, correlationId, ReplyStatus.COMPLETED, Map.copyOf(data), null);
    }

    public static CommandReply failed(UUID commandId, UUID correlationId, String error) {
        return new CommandReply(commandId, correlationId, ReplyStatus.FAILED, Map.of(), error);
    }

    public static CommandReply timedOut(UUID commandId, UUID correlationId, String error) {
        return new CommandReply(commandId, correlationId, ReplyStatus.TIMED_OUT, Map.of(), error);
    }

    public boolean isSuccess() {
        return status == ReplyStatus.COMPLETED;
    }

    public boolean isFailure() {
        return status == ReplyStatus.FAILED || status == ReplyStatus.TIMED_OUT;
    }

    /**
     * Serialize to JSON for sending over message queue
     */
    public String toJson() {
        return Jsons.toJson(this);
    }
}
