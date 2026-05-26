package com.hongik.grad.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.hongik.grad.auth.principal.AuthUser;
import com.hongik.grad.folders.domain.FolderRepository;
import com.hongik.grad.folders.domain.FolderType;
import com.hongik.grad.support.TestcontainersConfig;
import com.hongik.grad.users.application.OnboardingService;
import com.hongik.grad.users.domain.ProfileRepository;
import com.hongik.grad.users.dto.OnboardingRequest;
import com.hongik.grad.users.dto.ProfileResponse;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test-db")
@Import(TestcontainersConfig.class)
class OnboardingServiceIntegrationTest {

  @Autowired private OnboardingService service;
  @Autowired private ProfileRepository profileRepository;
  @Autowired private FolderRepository folderRepository;

  @Test
  @DisplayName("최초 호출은 Profile + 기본 폴더를 1개씩 생성한다")
  void firstCallCreatesProfileAndDefaultFolder() {
    UUID userId = UUID.randomUUID();
    AuthUser auth = new AuthUser(userId, "first@example.com");

    ProfileResponse response = service.onboard(auth, new OnboardingRequest("처음닉네임"));

    assertThat(response.userId()).isEqualTo(userId);
    assertThat(response.nickname()).isEqualTo("처음닉네임");
    assertThat(response.defaultFolderId()).isNotNull();
    assertThat(response.createdAt()).isNotNull();
    assertThat(profileRepository.findById(userId)).isPresent();
    assertThat(folderRepository.existsByUserIdAndType(userId, FolderType.DEFAULT)).isTrue();
  }

  @Test
  @DisplayName("두 번째 호출은 nickname 만 갱신하고 기본 폴더 ID 는 유지된다 (idempotent)")
  void secondCallIsIdempotent() {
    UUID userId = UUID.randomUUID();
    AuthUser auth = new AuthUser(userId, "again@example.com");

    ProfileResponse first = service.onboard(auth, new OnboardingRequest("first"));
    ProfileResponse second = service.onboard(auth, new OnboardingRequest("second"));

    assertThat(second.userId()).isEqualTo(userId);
    assertThat(second.nickname()).isEqualTo("second");
    assertThat(second.defaultFolderId()).isEqualTo(first.defaultFolderId());

    assertThat(profileRepository.findById(userId).orElseThrow().getNickname()).isEqualTo("second");
    long folderCount =
        folderRepository.findAll().stream().filter(f -> f.getUserId().equals(userId)).count();
    assertThat(folderCount).isEqualTo(1L);
  }
}
