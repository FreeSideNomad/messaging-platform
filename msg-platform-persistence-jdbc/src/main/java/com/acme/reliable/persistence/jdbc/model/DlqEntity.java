package com.acme.reliable.persistence.jdbc.model;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.DataType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistence model for DLQ (Dead Letter Queue) table.
 * Contains Micronaut Data annotations for JDBC mapping.
 */
@MappedEntity("command_dlq")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DlqEntity {

    @Id
    @AutoPopulated
    private UUID id;

    @MappedProperty(value = "command_id", type = DataType.UUID)
    private UUID commandId;

    @MappedProperty(value = "command_name", type = DataType.STRING)
    private String commandName;

    @MappedProperty(value = "business_key", type = DataType.STRING)
    private String businessKey;

    @MappedProperty(type = DataType.JSON)
    private String payload;

    @MappedProperty(value = "failed_status", type = DataType.STRING)
    private String failedStatus;

    @MappedProperty(value = "error_class", type = DataType.STRING)
    private String errorClass;

    @Nullable
    @MappedProperty(value = "error_message", type = DataType.STRING)
    private String errorMessage;

    @MappedProperty(type = DataType.INTEGER)
    private int attempts;

    @MappedProperty(value = "parked_by", type = DataType.STRING)
    private String parkedBy;

    @DateCreated
    @MappedProperty(value = "parked_at", type = DataType.TIMESTAMP)
    private Instant parkedAt;
}
