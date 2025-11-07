package com.acme.reliable.persistence.jdbc.mapper;

import com.acme.reliable.domain.Outbox;
import com.acme.reliable.persistence.jdbc.model.OutboxEntity;

/** Maps between Outbox domain object and OutboxEntity persistence model. */
public class OutboxMapper {

  public static OutboxEntity toEntity(Outbox domain) {
    if (domain == null) {
      return null;
    }
    OutboxEntity entity = new OutboxEntity();
    entity.setId(domain.getId());
    entity.setCategory(domain.getCategory());
    entity.setTopic(domain.getTopic());
    entity.setKey(domain.getKey());
    entity.setType(domain.getType());
    entity.setPayload(domain.getPayload());
    entity.setHeaders(domain.getHeaders());
    entity.setStatus(domain.getStatus());
    entity.setAttempts(domain.getAttempts());
    entity.setNextAt(domain.getNextAt());
    entity.setClaimedBy(domain.getClaimedBy());
    entity.setClaimedAt(null); // Domain doesn't have claimedAt yet
    entity.setCreatedAt(domain.getCreatedAt());
    entity.setPublishedAt(domain.getPublishedAt());
    entity.setLastError(domain.getLastError());
    return entity;
  }

  public static Outbox toDomain(OutboxEntity entity) {
    if (entity == null) {
      return null;
    }
    Outbox domain = new Outbox();
    domain.setId(entity.getId());
    domain.setCategory(entity.getCategory());
    domain.setTopic(entity.getTopic());
    domain.setKey(entity.getKey());
    domain.setType(entity.getType());
    domain.setPayload(entity.getPayload());
    domain.setHeaders(entity.getHeaders());
    domain.setStatus(entity.getStatus());
    domain.setAttempts(entity.getAttempts());
    domain.setNextAt(entity.getNextAt());
    domain.setClaimedBy(entity.getClaimedBy());
    domain.setCreatedAt(entity.getCreatedAt());
    domain.setPublishedAt(entity.getPublishedAt());
    domain.setLastError(entity.getLastError());
    return domain;
  }
}
