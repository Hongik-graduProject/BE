package com.hongik.grad.auth;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongik.grad.auth.api.AuthController;
import com.hongik.grad.auth.application.AuthService;
import com.hongik.grad.auth.dto.AuthUserDto;
import com.hongik.grad.auth.dto.SignupResponse;
import com.hongik.grad.auth.dto.TokenResponse;
import com.hongik.grad.auth.filter.JwtAuthenticationFilter;
import com.hongik.grad.auth.handler.JsonAccessDeniedHandler;
import com.hongik.grad.auth.handler.JsonAuthenticationEntryPoint;
import com.hongik.grad.auth.provider.JwtVerifier;
import com.hongik.grad.common.api.ErrorCode;
import com.hongik.grad.common.exception.DomainException;
import com.hongik.grad.config.SecurityConfig;
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

@WebMvcTest(AuthController.class)
@Import({
  SecurityConfig.class,
  JwtVerifier.class,
  JwtAuthenticationFilter.class,
  JsonAuthenticationEntryPoint.class,
  JsonAccessDeniedHandler.class
})
@ActiveProfiles("test")
class AuthControllerSliceTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockBean private AuthService authService;

  private static final UUID USER_ID = UUID.randomUUID();
  private static final String TOKEN =
      "Bearer " + JwtTestFactory.validToken(USER_ID, "test@test.com");

  @Test
  @DisplayName("POST /auth/signup — 성공 → 200 + user_id/email/nickname/created_at")
  void signup_success() throws Exception {
    when(authService.signup("a@b.com", "secret1", "kim"))
        .thenReturn(
            new SignupResponse(USER_ID.toString(), "a@b.com", "kim", "2026-06-14T00:00:00Z"));

    mockMvc
        .perform(
            post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("email", "a@b.com", "password", "secret1", "nickname", "kim"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("success"))
        .andExpect(jsonPath("$.data.user_id").value(USER_ID.toString()))
        .andExpect(jsonPath("$.data.email").value("a@b.com"))
        .andExpect(jsonPath("$.data.nickname").value("kim"))
        .andExpect(jsonPath("$.data.created_at").value("2026-06-14T00:00:00Z"));
  }

  @Test
  @DisplayName("POST /auth/signup — 이메일 중복 → 409 DUPLICATE")
  void signup_duplicate_409() throws Exception {
    when(authService.signup(eq("dup@b.com"), eq("secret1"), eq(null)))
        .thenThrow(new DomainException(ErrorCode.DUPLICATE, "already registered"));

    mockMvc
        .perform(
            post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("email", "dup@b.com", "password", "secret1"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("DUPLICATE"));
  }

  @Test
  @DisplayName("POST /auth/signup — 이메일 형식 오류 → 400 INVALID_PARAM")
  void signup_invalidEmail_400() throws Exception {
    mockMvc
        .perform(
            post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("email", "not-an-email", "password", "secret1"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_PARAM"));
  }

  @Test
  @DisplayName("POST /auth/login — 성공 → 200 + 토큰 + user 블록")
  void login_success() throws Exception {
    when(authService.login("a@b.com", "secret1"))
        .thenReturn(
            new TokenResponse(
                "acc",
                "ref",
                "Bearer",
                3600,
                new AuthUserDto(USER_ID.toString(), "a@b.com", "kim")));

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("email", "a@b.com", "password", "secret1"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.access_token").value("acc"))
        .andExpect(jsonPath("$.data.refresh_token").value("ref"))
        .andExpect(jsonPath("$.data.token_type").value("Bearer"))
        .andExpect(jsonPath("$.data.expires_in").value(3600))
        .andExpect(jsonPath("$.data.user.id").value(USER_ID.toString()))
        .andExpect(jsonPath("$.data.user.nickname").value("kim"));
  }

  @Test
  @DisplayName("POST /auth/login — 자격 오류 → 401 AUTH_INVALID")
  void login_invalid_401() throws Exception {
    when(authService.login("a@b.com", "wrong"))
        .thenThrow(new DomainException(ErrorCode.AUTH_INVALID, "invalid"));

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("email", "a@b.com", "password", "wrong"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("AUTH_INVALID"));
  }

  @Test
  @DisplayName("POST /auth/refresh — 성공 → 200 + 새 토큰 (user 없음)")
  void refresh_success() throws Exception {
    when(authService.refresh("ref"))
        .thenReturn(new TokenResponse("acc2", "ref2", "Bearer", 3600, null));

    mockMvc
        .perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refresh_token", "ref"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.access_token").value("acc2"))
        .andExpect(jsonPath("$.data.refresh_token").value("ref2"))
        .andExpect(jsonPath("$.data.user").doesNotExist());
  }

  @Test
  @DisplayName("POST /auth/logout — 인증 토큰으로 → 200 + service.logout 호출")
  void logout_success() throws Exception {
    mockMvc
        .perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("로그아웃되었습니다"));

    verify(authService).logout(TOKEN.substring(7));
  }

  @Test
  @DisplayName("POST /auth/logout — 토큰 없으면 401 (authenticated 보호)")
  void logout_noToken_401() throws Exception {
    mockMvc.perform(post("/auth/logout")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /auth/check-email — 미사용 이메일 → available true")
  void checkEmail_available() throws Exception {
    when(authService.isEmailAvailable("free@b.com")).thenReturn(true);

    mockMvc
        .perform(get("/auth/check-email").param("email", "free@b.com"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.available").value(true));
  }

  @Test
  @DisplayName("GET /auth/check-email — 사용중 이메일 → available false")
  void checkEmail_taken() throws Exception {
    when(authService.isEmailAvailable("taken@b.com")).thenReturn(false);

    mockMvc
        .perform(get("/auth/check-email").param("email", "taken@b.com"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.available").value(false));
  }

  @Test
  @DisplayName("DELETE /auth/account — 인증 사용자 → 200 + service.deleteAccount 호출")
  void deleteAccount_success() throws Exception {
    mockMvc
        .perform(
            delete("/auth/account")
                .header(HttpHeaders.AUTHORIZATION, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("password", "secret1"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("회원 탈퇴가 완료되었습니다"));

    verify(authService).deleteAccount(eq(USER_ID));
  }

  @Test
  @DisplayName("DELETE /auth/account — 토큰 없으면 401")
  void deleteAccount_noToken_401() throws Exception {
    mockMvc
        .perform(delete("/auth/account").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }
}
