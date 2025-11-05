package com.acme.reliable.process;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable value object representing a process instance state. Each modification returns a new
 * instance (copy-on-write).
 */
public record ProcessInstance(
    UUID processId,
    String processType,
    String businessKey,
    ProcessStatus status,
    String currentStep,
    Map<String, Object> data,
    int retries,
    Instant createdAt,
    Instant updatedAt) {
  /** Create new process instance */
  public static ProcessInstance create(
      UUID processId,
      String processType,
      String businessKey,
      String initialStep,
      Map<String, Object> initialData) {
    Instant now = Instant.now();
    return new ProcessInstance(
        processId,
        processType,
        businessKey,
        ProcessStatus.NEW,
        initialStep,
        Map.copyOf(initialData),
        0,
        now,
        now);
  }

  /** Update status */
  public ProcessInstance withStatus(ProcessStatus newStatus) {
    return new ProcessInstance(
        processId,
        processType,
        businessKey,
        newStatus,
        currentStep,
        data,
        retries,
        createdAt,
        Instant.now());
  }

  /** Move to next step */
  public ProcessInstance withCurrentStep(String newStep) {
    return new ProcessInstance(
        processId,
        processType,
        businessKey,
        status,
        newStep,
        data,
        retries,
        createdAt,
        Instant.now());
  }

  /** Update working data */
  public ProcessInstance withData(Map<String, Object> newData) {
    return new ProcessInstance(
        processId,
        processType,
        businessKey,
        status,
        currentStep,
        Map.copyOf(newData),
        retries,
        createdAt,
        Instant.now());
  }

  /** Increment retry counter */
  public ProcessInstance withRetries(int newRetries) {
    return new ProcessInstance(
        processId,
        processType,
        businessKey,
        status,
        currentStep,
        data,
        newRetries,
        createdAt,
        Instant.now());
  }

  /** Update multiple fields at once */
  public ProcessInstance update(
      ProcessStatus newStatus, String newStep, Map<String, Object> newData, int newRetries) {
    return new ProcessInstance(
        processId,
        processType,
        businessKey,
        newStatus,
        newStep,
        Map.copyOf(newData),
        newRetries,
        createdAt,
        Instant.now());
  }
}
