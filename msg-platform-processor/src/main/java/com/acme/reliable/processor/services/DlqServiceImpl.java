package com.acme.reliable.processor.services;

import com.acme.reliable.repository.DlqRepository;
import com.acme.reliable.service.DlqService;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.util.UUID;

@Singleton
public class DlqServiceImpl implements DlqService {
  private final DlqRepository repository;

  public DlqServiceImpl(DlqRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional
  public void park(
      UUID commandId,
      String commandName,
      String businessKey,
      String payload,
      String failedStatus,
      String errorClass,
      String errorMessage,
      int attempts,
      String parkedBy) {
    repository.insertDlqEntry(
        commandId,
        commandName,
        businessKey,
        payload,
        failedStatus,
        errorClass,
        errorMessage,
        attempts,
        parkedBy);
  }
}
