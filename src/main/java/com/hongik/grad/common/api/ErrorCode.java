package com.hongik.grad.common.api;

import org.springframework.http.HttpStatus;

/** Canonical error codes shared across the API. HTTP status is intrinsic to each code. */
public enum ErrorCode {
  INVALID_PARAM(HttpStatus.BAD_REQUEST),
  AUTH_EXPIRED(HttpStatus.UNAUTHORIZED),
  AUTH_INVALID(HttpStatus.UNAUTHORIZED),
  FORBIDDEN(HttpStatus.FORBIDDEN),
  NOT_FOUND(HttpStatus.NOT_FOUND),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
  DUPLICATE(HttpStatus.CONFLICT),
  RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

  private final HttpStatus status;

  ErrorCode(HttpStatus status) {
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}
