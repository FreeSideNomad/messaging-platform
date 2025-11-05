package com.acme.reliable.repository;

import java.util.UUID;

/** Repository for Dead Letter Queue - parking failed commands for manual intervention */
public interface DlqRepository {

  /**
   * Insert a failed command into the DLQ
   *
   * @param commandId the command ID
   * @param commandName the command name/type
   * @param businessKey the business key
   * @param payload the command payload
   * @param failedStatus the status when it failed (e.g., FAILED, TIMED_OUT)
   * @param errorClass the exception class name
   * @param errorMessage the error message
   * @param attempts number of attempts before parking
   * @param parkedBy the component that parked this command
   */
  void insertDlqEntry(
      UUID commandId,
      String commandName,
      String businessKey,
      String payload,
      String failedStatus,
      String errorClass,
      String errorMessage,
      int attempts,
      String parkedBy);
}
