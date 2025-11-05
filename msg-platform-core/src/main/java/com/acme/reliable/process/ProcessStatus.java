package com.acme.reliable.process;

/** Status of a process instance lifecycle */
public enum ProcessStatus {
  /** Process created but not yet started */
  NEW,

  /** Process is actively executing steps */
  RUNNING,

  /** Process completed successfully */
  SUCCEEDED,

  /** Process failed permanently */
  FAILED,

  /** Process is executing compensation steps */
  COMPENSATING,

  /** Process compensation completed */
  COMPENSATED,

  /** Process paused by operator */
  PAUSED
}
