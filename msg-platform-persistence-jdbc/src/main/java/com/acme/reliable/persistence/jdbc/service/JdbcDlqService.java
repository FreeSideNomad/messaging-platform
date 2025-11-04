package com.acme.reliable.persistence.jdbc.service;

import com.acme.reliable.service.DlqService;
import com.acme.reliable.persistence.jdbc.JdbcDlqRepository;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

import java.util.UUID;

@Singleton
public class JdbcDlqService implements DlqService {
    private final JdbcDlqRepository repository;

    public JdbcDlqService(JdbcDlqRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void park(UUID commandId, String commandName, String businessKey, String payload,
                     String failedStatus, String errorClass, String errorMessage, int attempts, String parkedBy) {
        repository.insertDlqEntry(commandId, commandName, businessKey, payload,
            failedStatus, errorClass, errorMessage, attempts, parkedBy);
    }
}
