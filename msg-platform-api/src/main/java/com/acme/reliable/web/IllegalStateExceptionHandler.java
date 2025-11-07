package com.acme.reliable.web;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

/**
 * Global exception handler for IllegalStateException.
 *
 * <p>Maps specific IllegalStateException messages to appropriate HTTP status codes: - "Duplicate
 * idempotency key" -> 409 CONFLICT - All other IllegalStateException -> 400 BAD REQUEST
 */
@Produces
@Singleton
@Requires(classes = {IllegalStateException.class, ExceptionHandler.class})
public class IllegalStateExceptionHandler
    implements ExceptionHandler<IllegalStateException, HttpResponse<ErrorResponse>> {

  @Override
  public HttpResponse<ErrorResponse> handle(HttpRequest request, IllegalStateException exception) {
    String message = exception.getMessage();

    // Map duplicate idempotency key to 409 Conflict
    if (message != null && message.contains("Duplicate idempotency key")) {
      return HttpResponse.status(HttpStatus.CONFLICT)
          .body(new ErrorResponse("Duplicate idempotency key", HttpStatus.CONFLICT.getCode()));
    }

    // All other IllegalStateException cases -> 400 Bad Request
    return HttpResponse.badRequest(
        new ErrorResponse(
            message != null ? message : "Invalid state", HttpStatus.BAD_REQUEST.getCode()));
  }
}
