package com.acme.reliable.processor.services;

import com.acme.reliable.repository.InboxRepository;
import com.acme.reliable.service.InboxService;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

@Singleton
public class InboxServiceImpl implements InboxService {
    private final InboxRepository repository;

    public InboxServiceImpl(InboxRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public boolean markIfAbsent(String messageId, String handler) {
        return repository.insertIfAbsent(messageId, handler) == 1;
    }
}
