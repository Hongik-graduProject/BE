package com.hongik.grad.notifications;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongik.grad.auth.filter.SupabaseJwtAuthenticationFilter;
import com.hongik.grad.auth.handler.JsonAccessDeniedHandler;
import com.hongik.grad.auth.handler.JsonAuthenticationEntryPoint;
import com.hongik.grad.auth.provider.SupabaseJwtVerifier;
import com.hongik.grad.config.SecurityConfig;
import com.hongik.grad.notifications.api.FcmTokenController;
import com.hongik.grad.notifications.application.FcmTokenService;
import com.hongik.grad.support.JwtTestFactory;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FcmTokenController.class)
@Import({
  SecurityConfig.class,
  SupabaseJwtVerifier.class,
  SupabaseJwtAuthenticationFilter.class,
  JsonAuthenticationEntryPoint.class,
  JsonAccessDeniedHandler.class
})
@ActiveProfiles("test")
class FcmTokenControllerSliceTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockBean private FcmTokenService fcmTokenService;

  private static final UUID USER_ID = UUID.randomUUID();
  private static final String TOKEN =
      "Bearer " + JwtTestFactory.validToken(USER_ID, "test@test.com");

  @Test
  @DisplayName("POST /notifications/fcm-token — 등록 성공 → 200 + service.register 호출")
  void register_success() throws Exception {
    mockMvc
        .perform(
            post("/notifications/fcm-token")
                .header(HttpHeaders.AUTHORIZATION, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("device_token", "dev-1", "platform", "ios"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("디바이스 토큰이 등록되었습니다"));

    verify(fcmTokenService).register(eq(USER_ID), eq("dev-1"), eq("ios"));
  }

  @Test
  @DisplayName("POST /notifications/fcm-token — device_token 누락 → 400")
  void register_missingToken_400() throws Exception {
    mockMvc
        .perform(
            post("/notifications/fcm-token")
                .header(HttpHeaders.AUTHORIZATION, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("platform", "ios"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_PARAM"));
  }

  @Test
  @DisplayName("POST /notifications/fcm-token — 잘못된 platform → 400")
  void register_invalidPlatform_400() throws Exception {
    mockMvc
        .perform(
            post("/notifications/fcm-token")
                .header(HttpHeaders.AUTHORIZATION, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("device_token", "dev-1", "platform", "windows"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_PARAM"));
  }

  @Test
  @DisplayName("DELETE /notifications/fcm-token — 삭제 성공(멱등) → 200")
  void delete_success() throws Exception {
    mockMvc
        .perform(
            delete("/notifications/fcm-token")
                .header(HttpHeaders.AUTHORIZATION, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("device_token", "dev-1"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("디바이스 토큰이 삭제되었습니다"));

    verify(fcmTokenService).delete(eq(USER_ID), eq("dev-1"));
  }

  @Test
  @DisplayName("POST /notifications/fcm-token — 토큰 없으면 401")
  void register_noToken_401() throws Exception {
    mockMvc
        .perform(
            post("/notifications/fcm-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("device_token", "dev-1", "platform", "ios"))))
        .andExpect(status().isUnauthorized());
  }
}
