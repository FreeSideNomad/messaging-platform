package com.acme.reliable.processor.services;

import com.acme.reliable.core.Jsons;
import com.acme.reliable.domain.Outbox;
import com.acme.reliable.repository.OutboxRepository;
import com.acme.reliable.service.OutboxService;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

/**
 * Service implementation for Outbox pattern operations.
 *
 * <p>This service acts as the bridge between the application layer (OutboxService interface) and
 * the persistence layer (OutboxRepository interface). It converts between domain objects and
 * repository parameters, and handles transactional concerns.
 */
@Singleton
public class OutboxServiceImpl implements OutboxService {
    private final OutboxRepository repository;

    public OutboxServiceImpl(OutboxRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public long addReturningId(Outbox outbox) {
        var headersJson =
                outbox.getHeaders() != null && !outbox.getHeaders().isEmpty()
                        ? Jsons.toJson(outbox.getHeaders())
                        : "{}";
        return repository.insertReturningId(
                outbox.getCategory(),
                outbox.getTopic(),
                outbox.getKey(),
                outbox.getType(),
                outbox.getPayload(),
                headersJson);
    }

    @Override
    @Transactional
    public Optional<Outbox> claimOne(long id) {
        return repository.claimOne(id);
    }

    @Override
    @Transactional
    public List<Outbox> claim(int max, String claimer) {
        return repository.claim(max, claimer);
    }

    @Override
    @Transactional
    public void markPublished(long id) {
        repository.markPublished(id);
    }

    @Override
    @Transactional
    public void reschedule(long id, long backoffMs, String error) {
        repository.reschedule(id, backoffMs, error);
    }
}
