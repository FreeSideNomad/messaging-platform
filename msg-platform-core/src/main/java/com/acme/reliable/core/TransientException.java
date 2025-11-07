package com.acme.reliable.core;

public class TransientException extends RuntimeException {
  public TransientException(String message) {
    super(message);
  }

  public TransientException(String message, Throwable e) {
    super(message, e);
  }
}
