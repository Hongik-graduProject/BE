package com.hongik.grad.common.exception;

import com.hongik.grad.common.api.ApiResponse;
import com.hongik.grad.common.api.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining(", "));
    return respond(ErrorCode.INVALID_PARAM, message.isBlank() ? "Invalid request" : message);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
    return respond(ErrorCode.INVALID_PARAM, ex.getMessage());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(
      HttpMessageNotReadableException ex) {
    log.debug("Malformed request body: {}", ex.getMostSpecificCause().getMessage());
    return respond(ErrorCode.INVALID_PARAM, "Malformed JSON request");
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException ex) {
    return respond(ErrorCode.METHOD_NOT_ALLOWED, ex.getMessage());
  }

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ApiResponse<Void>> handleDomain(DomainException ex) {
    return respond(ex.code(), ex.getMessage());
  }

  // TODO PR-03: handle AuthenticationException / AccessDeniedException
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
    log.error("Unhandled exception", ex);
    return respond(ErrorCode.INTERNAL_ERROR, "Internal server error");
  }

  private ResponseEntity<ApiResponse<Void>> respond(ErrorCode code, String message) {
    return ResponseEntity.status(code.status()).body(ApiResponse.error(code, message));
  }
}
