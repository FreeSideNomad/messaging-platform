package com.acme.reliable.persistence.jdbc.service;

import com.acme.reliable.service.InboxService;
import com.acme.reliable.persistence.jdbc.JdbcInboxRepository;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

@Singleton
public class JdbcInboxService implements InboxService {
    private final JdbcInboxRepository repository;

    public JdbcInboxService(JdbcInboxRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public boolean markIfAbsent(String messageId, String handler) {
        return repository.insertIfAbsent(messageId, handler) == 1;
    }
}
