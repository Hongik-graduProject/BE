package com.hongik.grad.auth.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongik.grad.common.api.ApiResponse;
import com.hongik.grad.common.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Emits a JSON {@link ApiResponse} when an authenticated user lacks permission. */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    response.setStatus(ErrorCode.FORBIDDEN.status().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(
        response.getOutputStream(), ApiResponse.error(ErrorCode.FORBIDDEN, "Access denied"));
  }
}
