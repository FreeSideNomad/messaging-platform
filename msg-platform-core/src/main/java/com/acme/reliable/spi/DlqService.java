package com.acme.reliable.spi;

import com.acme.reliable.domain.DlqRepository;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

import java.util.UUID;

@Singleton
public class DlqService {
    private final DlqRepository repository;

    public DlqService(DlqRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void park(UUID commandId, String commandName, String businessKey, String payload,
                     String failedStatus, String errorClass, String errorMessage, int attempts, String parkedBy) {
        repository.insertDlqEntry(commandId, commandName, businessKey, payload,
            failedStatus, errorClass, errorMessage, attempts, parkedBy);
    }
}
