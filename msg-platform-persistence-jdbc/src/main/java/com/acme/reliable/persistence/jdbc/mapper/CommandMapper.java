package com.acme.reliable.persistence.jdbc.mapper;

import com.acme.reliable.domain.Command;
import com.acme.reliable.persistence.jdbc.model.CommandEntity;

/**
 * Maps between Command domain object and CommandEntity persistence model.
 */
public class CommandMapper {

    public static CommandEntity toEntity(Command domain) {
        if (domain == null) {
            return null;
        }
        return new CommandEntity(
            domain.getId(),
            domain.getName(),
            domain.getBusinessKey(),
            domain.getPayload(),
            domain.getIdempotencyKey(),
            domain.getStatus(),
            domain.getRequestedAt(),
            domain.getUpdatedAt(),
            domain.getRetries(),
            domain.getProcessingLeaseUntil(),
            domain.getLastError(),
            domain.getReply()
        );
    }

    public static Command toDomain(CommandEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Command(
            entity.getId(),
            entity.getName(),
            entity.getBusinessKey(),
            entity.getPayload(),
            entity.getIdempotencyKey(),
            entity.getStatus(),
            entity.getRequestedAt(),
            entity.getUpdatedAt(),
            entity.getRetries(),
            entity.getProcessingLeaseUntil(),
            entity.getLastError(),
            entity.getReply()
        );
    }
}
