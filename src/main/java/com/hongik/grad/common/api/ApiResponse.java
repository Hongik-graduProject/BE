package com.hongik.grad.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard wire format for every HTTP response.
 *
 * <p>{@code status} is one of {@code "success"} or {@code "error"}.
 *
 * <p>On success: {@code data} carries the payload, {@code message} optional, {@code error} null. On
 * error: {@code error} carries the {@link ErrorCode} name, {@code message} human readable, {@code
 * data} null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String status, T data, String message, String error) {

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>("success", data, null, null);
  }

  public static <T> ApiResponse<T> success(T data, String message) {
    return new ApiResponse<>("success", data, message, null);
  }

  public static <T> ApiResponse<T> error(ErrorCode code, String message) {
    return new ApiResponse<>("error", null, message, code.name());
  }
}
