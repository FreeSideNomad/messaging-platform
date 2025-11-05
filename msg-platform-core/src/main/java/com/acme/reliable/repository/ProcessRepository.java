package com.acme.reliable.repository;

import com.acme.reliable.process.ProcessEvent;
import com.acme.reliable.process.ProcessInstance;
import com.acme.reliable.process.ProcessLogEntry;
import com.acme.reliable.process.ProcessStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository for process instance persistence and event sourcing */
public interface ProcessRepository {

  /**
   * Insert new process instance with initial event
   *
   * @param instance the process instance to insert
   * @param initialEvent the first event (typically ProcessStarted)
   */
  void insert(ProcessInstance instance, ProcessEvent initialEvent);

  /**
   * Find process instance by ID
   *
   * @param processId the process ID
   * @return optional of process instance
   */
  Optional<ProcessInstance> findById(UUID processId);

  /**
   * Find process instances by status
   *
   * @param status the status to filter by
   * @param limit maximum number of results
   * @return list of process instances
   */
  List<ProcessInstance> findByStatus(ProcessStatus status, int limit);

  /**
   * Find process instances by type and status
   *
   * @param processType the process type
   * @param status the status
   * @param limit maximum number of results
   * @return list of process instances
   */
  List<ProcessInstance> findByTypeAndStatus(String processType, ProcessStatus status, int limit);

  /**
   * Update process instance and append event to log
   *
   * @param instance the updated process instance
   * @param event the event describing this update
   */
  void update(ProcessInstance instance, ProcessEvent event);

  /**
   * Get event log for a process
   *
   * @param processId the process ID
   * @return list of log entries in sequence order
   */
  List<ProcessLogEntry> getLog(UUID processId);

  /**
   * Get event log for a process with limit
   *
   * @param processId the process ID
   * @param limit maximum number of entries
   * @return list of log entries in sequence order
   */
  List<ProcessLogEntry> getLog(UUID processId, int limit);

  /**
   * Find process instance by business key
   *
   * @param processType the process type
   * @param businessKey the business key
   * @return optional of process instance
   */
  Optional<ProcessInstance> findByBusinessKey(String processType, String businessKey);
}
