package com.acme.reliable.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * DLQ (Dead Letter Queue) domain entity (pure domain object, no persistence annotations).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Dlq {

    private UUID id;
    private UUID commandId;
    private String commandName;
    private String businessKey;
    private String payload;
    private String failedStatus;
    private String errorClass;
    private String errorMessage;
    private int attempts;
    private String parkedBy;
    private Instant parkedAt;
}
