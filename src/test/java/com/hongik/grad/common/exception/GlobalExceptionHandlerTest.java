package com.hongik.grad.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    // TODO: switch to @WebMvcTest in PR-03 once CorsFilter + SecurityFilterChain are real
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("MethodArgumentNotValidException 발생 시 400 INVALID_PARAM 으로 응답한다")
  void methodArgumentNotValid() throws Exception {
    String body = mapper.writeValueAsString(new EchoRequest(""));

    mockMvc
        .perform(post("/__test/echo").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("error"))
        .andExpect(jsonPath("$.error").value("INVALID_PARAM"));
  }

  @Test
  @DisplayName("Malformed JSON 본문 수신 시 400 INVALID_PARAM 으로 응답한다")
  void malformedJsonBody() throws Exception {
    mockMvc
        .perform(post("/__test/echo").contentType(MediaType.APPLICATION_JSON).content("{not-json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("error"))
        .andExpect(jsonPath("$.error").value("INVALID_PARAM"));
  }

  @Test
  @DisplayName("지원하지 않는 HTTP 메서드 요청 시 405 METHOD_NOT_ALLOWED 로 응답한다")
  void methodNotSupported() throws Exception {
    mockMvc
        .perform(post("/__test/only-get"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.status").value("error"))
        .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
  }

  record EchoRequest(@NotBlank String name) {}

  @RestController
  static class TestController {
    @PostMapping("/__test/echo")
    EchoRequest echo(@Valid @RequestBody EchoRequest req) {
      return req;
    }

    @GetMapping("/__test/only-get")
    String onlyGet() {
      return "ok";
    }
  }
}
