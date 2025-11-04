package com.acme.reliable.persistence.jdbc.mapper;

import com.acme.reliable.domain.Dlq;
import com.acme.reliable.persistence.jdbc.model.DlqEntity;

/**
 * Maps between Dlq domain object and DlqEntity persistence model.
 */
public class DlqMapper {

    public static DlqEntity toEntity(Dlq domain) {
        if (domain == null) {
            return null;
        }
        return new DlqEntity(
            domain.getId(),
            domain.getCommandId(),
            domain.getCommandName(),
            domain.getBusinessKey(),
            domain.getPayload(),
            domain.getFailedStatus(),
            domain.getErrorClass(),
            domain.getErrorMessage(),
            domain.getAttempts(),
            domain.getParkedBy(),
            domain.getParkedAt()
        );
    }

    public static Dlq toDomain(DlqEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Dlq(
            entity.getId(),
            entity.getCommandId(),
            entity.getCommandName(),
            entity.getBusinessKey(),
            entity.getPayload(),
            entity.getFailedStatus(),
            entity.getErrorClass(),
            entity.getErrorMessage(),
            entity.getAttempts(),
            entity.getParkedBy(),
            entity.getParkedAt()
        );
    }
}
