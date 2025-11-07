package com.acme.reliable.web;

/** Simple payload for error responses returned by global exception handlers. */
public record ErrorResponse(String message, int statusCode) {}
