package com.hongik.grad.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  @DisplayName("success 응답은 status=success, data 페이로드, error=null 직렬화")
  void serializeSuccess() throws Exception {
    ApiResponse<String> response = ApiResponse.success("ok");

    String json = mapper.writeValueAsString(response);

    assertThat(json).contains("\"status\":\"success\"");
    assertThat(json).contains("\"data\":\"ok\"");
    assertThat(json).doesNotContain("\"error\"");
  }

  @Test
  @DisplayName("error 응답은 status=error, error 코드 이름, message 포함 직렬화")
  void serializeError() throws Exception {
    ApiResponse<Void> response = ApiResponse.error(ErrorCode.INVALID_PARAM, "bad input");

    String json = mapper.writeValueAsString(response);

    assertThat(json).contains("\"status\":\"error\"");
    assertThat(json).contains("\"error\":\"INVALID_PARAM\"");
    assertThat(json).contains("\"message\":\"bad input\"");
    assertThat(json).doesNotContain("\"data\"");
  }
}
