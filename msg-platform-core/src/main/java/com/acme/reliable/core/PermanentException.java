package com.acme.reliable.core;

public class PermanentException extends RuntimeException {
  public PermanentException(String message) {
    super(message);
  }

  public PermanentException(String message, Throwable e) {
    super(message, e);
  }
}
