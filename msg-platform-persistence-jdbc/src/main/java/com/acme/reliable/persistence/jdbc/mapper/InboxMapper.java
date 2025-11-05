package com.acme.reliable.persistence.jdbc.mapper;

import com.acme.reliable.domain.Inbox;
import com.acme.reliable.persistence.jdbc.model.InboxEntity;

/** Maps between Inbox domain object and InboxEntity persistence model. */
public class InboxMapper {

  public static InboxEntity toEntity(Inbox domain) {
    if (domain == null) {
      return null;
    }
    InboxEntity entity = new InboxEntity();
    entity.setId(toEntityId(domain.getId()));
    entity.setProcessedAt(domain.getProcessedAt());
    return entity;
  }

  public static Inbox toDomain(InboxEntity entity) {
    if (entity == null) {
      return null;
    }
    Inbox domain = new Inbox();
    domain.setId(toDomainId(entity.getId()));
    domain.setProcessedAt(entity.getProcessedAt());
    return domain;
  }

  private static InboxEntity.InboxId toEntityId(Inbox.InboxId domainId) {
    if (domainId == null) {
      return null;
    }
    return new InboxEntity.InboxId(domainId.getMessageId(), domainId.getHandler());
  }

  private static Inbox.InboxId toDomainId(InboxEntity.InboxId entityId) {
    if (entityId == null) {
      return null;
    }
    return new Inbox.InboxId(entityId.getMessageId(), entityId.getHandler());
  }
}
