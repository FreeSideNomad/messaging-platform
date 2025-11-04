package com.acme.reliable.persistence.jdbc.mapper;

import com.acme.reliable.domain.Outbox;
import com.acme.reliable.persistence.jdbc.model.OutboxEntity;

/**
 * Maps between Outbox domain object and OutboxEntity persistence model.
 */
public class OutboxMapper {

    public static OutboxEntity toEntity(Outbox domain) {
        if (domain == null) {
            return null;
        }
        return new OutboxEntity(
            domain.getId(),
            domain.getCategory(),
            domain.getTopic(),
            domain.getKey(),
            domain.getType(),
            domain.getPayload(),
            domain.getHeaders(),
            domain.getStatus(),
            domain.getAttempts(),
            domain.getNextAt(),
            domain.getClaimedBy(),
            domain.getCreatedAt(),
            domain.getPublishedAt(),
            domain.getLastError()
        );
    }

    public static Outbox toDomain(OutboxEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Outbox(
            entity.getId(),
            entity.getCategory(),
            entity.getTopic(),
            entity.getKey(),
            entity.getType(),
            entity.getPayload(),
            entity.getHeaders(),
            entity.getStatus(),
            entity.getAttempts(),
            entity.getNextAt(),
            entity.getClaimedBy(),
            entity.getCreatedAt(),
            entity.getPublishedAt(),
            entity.getLastError()
        );
    }
}
