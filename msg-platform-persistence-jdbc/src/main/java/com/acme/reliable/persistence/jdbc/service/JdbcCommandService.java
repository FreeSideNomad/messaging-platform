package com.acme.reliable.persistence.jdbc.service;

import com.acme.reliable.service.CommandService;
import com.acme.reliable.persistence.jdbc.JdbcCommandRepository;
import com.acme.reliable.persistence.jdbc.mapper.CommandMapper;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class JdbcCommandService implements CommandService {
    private final JdbcCommandRepository repository;

    public JdbcCommandService(JdbcCommandRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public UUID savePending(String name, String idem, String businessKey, String payload, String replyJson) {
        UUID id = UUID.randomUUID();
        repository.insertPending(id, name, businessKey, payload, idem, replyJson);
        return id;
    }

    @Transactional(readOnly = true)
    public Optional<Record> find(UUID id) {
        return repository.findById(id)
            .map(CommandMapper::toDomain)
            .map(c -> new Record(
                c.getId(),
                c.getName(),
                c.getBusinessKey(),
                c.getPayload(),
                c.getStatus(),
                c.getReply()
            ));
    }

    @Transactional
    public void markRunning(UUID id, Instant leaseUntil) {
        repository.updateToRunning(id, Timestamp.from(leaseUntil));
    }

    @Transactional
    public void markSucceeded(UUID id) {
        repository.updateToSucceeded(id);
    }

    @Transactional
    public void markFailed(UUID id, String error) {
        repository.updateToFailed(id, error);
    }

    @Transactional
    public void bumpRetry(UUID id, String error) {
        repository.incrementRetries(id, error);
    }

    @Transactional
    public void markTimedOut(UUID id, String reason) {
        repository.updateToTimedOut(id, reason);
    }

    @Transactional(readOnly = true)
    public boolean existsByIdempotencyKey(String k) {
        return repository.existsByIdempotencyKey(k);
    }
}
