package com.acme.reliable.spi;

import com.acme.reliable.domain.InboxRepository;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

@Singleton
public class InboxService {
    private final InboxRepository repository;

    public InboxService(InboxRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public boolean markIfAbsent(String messageId, String handler) {
        return repository.insertIfAbsent(messageId, handler) == 1;
    }
}
