package com.acme.reliable.processor.services;

import com.acme.reliable.repository.CommandRepository;
import com.acme.reliable.service.CommandService;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class CommandServiceImpl implements CommandService {
    private final CommandRepository repository;

    public CommandServiceImpl(CommandRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public UUID savePending(
            String name, String idem, String businessKey, String payload, String replyJson) {
        UUID id = UUID.randomUUID();
        repository.insertPending(id, name, businessKey, payload, idem, replyJson);
        return id;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Record> find(UUID id) {
        return repository.findById(id).map(c ->
                new Record(
                        c.getId(),
                        c.getName(),
                        c.getBusinessKey(),
                        c.getPayload(),
                        c.getStatus(),
                        c.getReply()));
    }

    @Override
    @Transactional
    public void markRunning(UUID id, Instant leaseUntil) {
        repository.updateToRunning(id, Timestamp.from(leaseUntil));
    }

    @Override
    @Transactional
    public void markSucceeded(UUID id) {
        repository.updateToSucceeded(id);
    }

    @Override
    @Transactional
    public void markFailed(UUID id, String error) {
        repository.updateToFailed(id, error);
    }

    @Override
    @Transactional
    public void bumpRetry(UUID id, String error) {
        repository.incrementRetries(id, error);
    }

    @Override
    @Transactional
    public void markTimedOut(UUID id, String reason) {
        repository.updateToTimedOut(id, reason);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByIdempotencyKey(String k) {
        return repository.existsByIdempotencyKey(k);
    }
}
