package com.hongik.grad.auth.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongik.grad.auth.filter.SupabaseJwtAuthenticationFilter;
import com.hongik.grad.common.api.ApiResponse;
import com.hongik.grad.common.api.ErrorCode;
import com.hongik.grad.common.exception.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Emits a JSON {@link ApiResponse} when no authenticated principal is present.
 *
 * <p>If {@link com.hongik.grad.auth.filter.SupabaseJwtAuthenticationFilter} attached a {@link
 * DomainException} (e.g. expired token) as the {@code authError} request attribute, its error code
 * is honored. Otherwise we fall back to {@link ErrorCode#AUTH_INVALID}.
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    Object attr = request.getAttribute(SupabaseJwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
    ErrorCode code = ErrorCode.AUTH_INVALID;
    String message = "Authentication required";
    if (attr instanceof DomainException de) {
      code = de.code();
      message = de.getMessage();
    }
    response.setStatus(code.status().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(code, message));
  }
}
