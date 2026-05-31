package com.hongik.grad.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/**
 * PR-10 golden path E2E: signup (Supabase JWT) → onboarding (POST /users) → folders (GET /folders).
 *
 * <p>Extends {@code AuthOnboardingE2ETest} (which stops at profile/folder persistence) by driving
 * the real {@code GET /folders} read path and asserting the spec §3 folder-list response keys:
 * exactly one DEFAULT folder with {@code item_count == 0} for a freshly onboarded user. No new
 * production code is exercised beyond the existing onboarding and folder-list endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test-db")
@Import(TestcontainersConfig.class)
class SignupGoldenPathE2ETest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ProfileRepository profileRepository;

  @Test
  @DisplayName(
      "signup→onboarding→folders: POST /users 후 GET /folders가 기본 폴더 1개(item_count=0)를 반환한다")
  void signupOnboardingFoldersGoldenPath() throws Exception {
    UUID userId = UUID.randomUUID();
    String token = "Bearer " + JwtTestFactory.validToken(userId, "golden@example.com");

    // 1) Onboarding — creates Profile + DEFAULT folder.
    mockMvc
        .perform(
            post("/users")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"골든유저\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("success"))
        .andExpect(jsonPath("$.data.user_id").value(userId.toString()))
        .andExpect(jsonPath("$.data.default_folder_id").isNumber());

    assertThat(profileRepository.findById(userId)).isPresent();

    // 2) Folder list — spec §3 keys for the freshly created DEFAULT folder.
    mockMvc
        .perform(get("/folders").header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("success"))
        .andExpect(jsonPath("$.data.folders.length()").value(1))
        .andExpect(jsonPath("$.data.folders[0].id").isNumber())
        .andExpect(jsonPath("$.data.folders[0].name").isNotEmpty())
        .andExpect(jsonPath("$.data.folders[0].type").value(FolderType.DEFAULT.json()))
        .andExpect(jsonPath("$.data.folders[0].item_count").value(0))
        .andExpect(jsonPath("$.data.folders[0].unread_disclosure_count").value(0));
  }
}
