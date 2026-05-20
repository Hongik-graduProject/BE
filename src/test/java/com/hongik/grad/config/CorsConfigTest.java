package com.hongik.grad.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsConfigTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("Vercel 도메인 preflight OPTIONS 요청은 200 응답")
  void corsPreflightFromVercel() throws Exception {
    mockMvc
        .perform(
            options("/actuator/health")
                .header(HttpHeaders.ORIGIN, "https://hongik-grad.vercel.app")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("localhost preflight OPTIONS 요청은 200 응답")
  void corsPreflightFromLocalhost() throws Exception {
    mockMvc
        .perform(
            options("/actuator/health")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(status().isOk());
  }
}
