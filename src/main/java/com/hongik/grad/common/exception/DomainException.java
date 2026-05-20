package com.hongik.grad.common.exception;

import com.hongik.grad.common.api.ErrorCode;

/** Base class for domain-level failures translated to API responses. */
public class DomainException extends RuntimeException {

  private final ErrorCode code;

  public DomainException(ErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public ErrorCode code() {
    return code;
  }
}
