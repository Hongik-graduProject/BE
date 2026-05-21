package com.hongik.grad.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hongik.grad.folders.domain.FolderRepository;
import com.hongik.grad.folders.domain.FolderType;
import com.hongik.grad.support.JwtTestFactory;
import com.hongik.grad.support.TestcontainersConfig;
import com.hongik.grad.users.domain.ProfileRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test-db")
@Import(TestcontainersConfig.class)
class AuthOnboardingE2ETest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ProfileRepository profileRepository;
  @Autowired private FolderRepository folderRepository;

  @Test
  @DisplayName("유효 토큰 POST /users → 200 + Profile 1 + 기본 폴더 1")
  void validTokenCreatesProfileAndFolder() throws Exception {
    UUID userId = UUID.randomUUID();
    String token = JwtTestFactory.validToken(userId, "ok@example.com");

    mockMvc
        .perform(
            post("/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"닉네임1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("success"))
        .andExpect(jsonPath("$.data.user_id").value(userId.toString()))
        .andExpect(jsonPath("$.data.nickname").value("닉네임1"))
        .andExpect(jsonPath("$.data.default_folder_id").isNumber());

    assertThat(profileRepository.findById(userId)).isPresent();
    assertThat(folderRepository.existsByUserIdAndType(userId, FolderType.DEFAULT)).isTrue();
  }

  @Test
  @DisplayName("같은 토큰 재호출은 nickname 만 갱신하고 폴더는 추가 생성하지 않는다")
  void repeatedCallIsIdempotent() throws Exception {
    UUID userId = UUID.randomUUID();
    String token = JwtTestFactory.validToken(userId, "same@example.com");

    mockMvc
        .perform(
            post("/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"닉넴구\"}"))
        .andExpect(status().isOk());

    Long firstFolderId =
        folderRepository.findByUserIdAndType(userId, FolderType.DEFAULT).orElseThrow().getId();

    mockMvc
        .perform(
            post("/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"닉넴신\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.nickname").value("닉넴신"))
        .andExpect(jsonPath("$.data.default_folder_id").value(firstFolderId));

    long folderCount =
        folderRepository.findAll().stream().filter(f -> f.getUserId().equals(userId)).count();
    assertThat(folderCount).isEqualTo(1L);
    assertThat(profileRepository.findById(userId).orElseThrow().getNickname()).isEqualTo("닉넴신");
  }

  @Test
  @DisplayName("Authorization 헤더 없는 POST /users → 401 AUTH_INVALID")
  void missingHeaderReturns401() throws Exception {
    mockMvc
        .perform(post("/users").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("AUTH_INVALID"));
  }

  @Test
  @DisplayName("만료 토큰은 401 AUTH_EXPIRED")
  void expiredTokenReturns401Expired() throws Exception {
    String token = JwtTestFactory.expiredToken(UUID.randomUUID());

    mockMvc
        .perform(
            post("/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"x\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("AUTH_EXPIRED"));
  }

  @Test
  @DisplayName("aud=anon 토큰은 401 AUTH_INVALID")
  void anonAudienceReturns401() throws Exception {
    String token = JwtTestFactory.anonToken(UUID.randomUUID());

    mockMvc
        .perform(
            post("/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"x\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("AUTH_INVALID"));
  }

  @Test
  @DisplayName("위조 서명 토큰은 401 AUTH_INVALID")
  void forgedSignatureReturns401() throws Exception {
    String token = JwtTestFactory.forgedToken(UUID.randomUUID());

    mockMvc
        .perform(
            post("/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"x\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("AUTH_INVALID"));
  }

  @Test
  @DisplayName("유효 토큰 + 짧은 nickname 본문은 400 INVALID_PARAM")
  void shortNicknameReturns400() throws Exception {
    UUID userId = UUID.randomUUID();
    String token = JwtTestFactory.validToken(userId, "v@example.com");

    mockMvc
        .perform(
            post("/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"a\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_PARAM"));
  }
}
