package com.hongik.grad.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hongik.grad.auth.application.AuthService;
import com.hongik.grad.auth.dto.SignupResponse;
import com.hongik.grad.auth.dto.TokenResponse;
import com.hongik.grad.auth.provider.JwtProvider;
import com.hongik.grad.auth.provider.JwtVerifier;
import com.hongik.grad.common.api.ErrorCode;
import com.hongik.grad.common.exception.DomainException;
import com.hongik.grad.support.JwtTestFactory;
import com.hongik.grad.users.domain.Profile;
import com.hongik.grad.users.domain.ProfileRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Self-managed auth against the local profiles table (Supabase GoTrue removed). */
class AuthServiceTest {

  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
  private final JwtProvider jwtProvider =
      new JwtProvider(JwtTestFactory.TEST_SECRET, Duration.ofHours(1), Duration.ofDays(30));
  private final JwtVerifier jwtVerifier = new JwtVerifier(JwtTestFactory.TEST_SECRET);

  private ProfileRepository profileRepository;
  private AuthService authService;

  @BeforeEach
  void setUp() {
    profileRepository = mock(ProfileRepository.class);
    authService = new AuthService(profileRepository, passwordEncoder, jwtProvider, jwtVerifier);
  }

  @Test
  @DisplayName("가입 시 이메일은 소문자로 정규화되고 비밀번호는 해시로만 저장된다")
  void signupNormalizesEmailAndHashesPassword() {
    when(profileRepository.existsByEmail("a@b.com")).thenReturn(false);
    when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

    SignupResponse response = authService.signup("  A@B.com ", "secret1", "kim");

    ArgumentCaptor<Profile> saved = ArgumentCaptor.forClass(Profile.class);
    verify(profileRepository).save(saved.capture());
    assertThat(saved.getValue().getEmail()).isEqualTo("a@b.com");
    assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("secret1");
    assertThat(passwordEncoder.matches("secret1", saved.getValue().getPasswordHash())).isTrue();
    assertThat(response.email()).isEqualTo("a@b.com");
    assertThat(response.nickname()).isEqualTo("kim");
  }

  @Test
  @DisplayName("닉네임을 주지 않으면 이메일 local part 를 쓴다")
  void signupFallsBackToEmailLocalPart() {
    when(profileRepository.existsByEmail("hongik@example.com")).thenReturn(false);
    when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

    assertThat(authService.signup("hongik@example.com", "secret1", null).nickname())
        .isEqualTo("hongik");
  }

  @Test
  @DisplayName("이미 가입된 이메일이면 DUPLICATE")
  void signupRejectsDuplicateEmail() {
    when(profileRepository.existsByEmail("a@b.com")).thenReturn(true);

    assertThatThrownBy(() -> authService.signup("A@B.com", "secret1", "kim"))
        .isInstanceOf(DomainException.class)
        .extracting(e -> ((DomainException) e).code())
        .isEqualTo(ErrorCode.DUPLICATE);
  }

  @Test
  @DisplayName("없는 이메일 로그인은 AUTH_INVALID")
  void loginUnknownEmail() {
    when(profileRepository.findByEmail("nobody@b.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login("nobody@b.com", "secret1"))
        .isInstanceOf(DomainException.class)
        .extracting(e -> ((DomainException) e).code())
        .isEqualTo(ErrorCode.AUTH_INVALID);
  }

  @Test
  @DisplayName("비밀번호가 틀리면 AUTH_INVALID")
  void loginWrongPassword() {
    when(profileRepository.findByEmail("a@b.com")).thenReturn(Optional.of(existingProfile()));

    assertThatThrownBy(() -> authService.login("a@b.com", "wrong-password"))
        .isInstanceOf(DomainException.class)
        .extracting(e -> ((DomainException) e).code())
        .isEqualTo(ErrorCode.AUTH_INVALID);
  }

  @Test
  @DisplayName("로그인 성공 시 검증 가능한 액세스·리프레시 토큰과 user 블록을 준다")
  void loginSuccess() {
    Profile profile = existingProfile();
    when(profileRepository.findByEmail("a@b.com")).thenReturn(Optional.of(profile));

    TokenResponse tokens = authService.login("a@b.com", "secret1");

    assertThat(tokens.tokenType()).isEqualTo("Bearer");
    assertThat(tokens.user()).isNotNull();
    assertThat(tokens.user().email()).isEqualTo("a@b.com");
    assertThat(jwtVerifier.verify(tokens.accessToken()).sub()).isEqualTo(profile.getId());
    assertThat(jwtVerifier.verifyRefresh(tokens.refreshToken()).sub()).isEqualTo(profile.getId());
  }

  @Test
  @DisplayName("재발급은 새 토큰 쌍을 주고 user 블록은 비운다")
  void refreshIssuesNewPair() {
    Profile profile = existingProfile();
    when(profileRepository.findById(profile.getId())).thenReturn(Optional.of(profile));

    TokenResponse tokens = authService.refresh(jwtProvider.createRefreshToken(profile.getId()));

    assertThat(tokens.user()).isNull();
    assertThat(jwtVerifier.verify(tokens.accessToken()).sub()).isEqualTo(profile.getId());
  }

  @Test
  @DisplayName("탈퇴한 계정의 리프레시 토큰은 더 이상 통하지 않는다")
  void refreshFailsAfterAccountDeleted() {
    UUID userId = UUID.randomUUID();
    when(profileRepository.findById(userId)).thenReturn(Optional.empty());
    String refresh = jwtProvider.createRefreshToken(userId);

    assertThatThrownBy(() -> authService.refresh(refresh))
        .isInstanceOf(DomainException.class)
        .extracting(e -> ((DomainException) e).code())
        .isEqualTo(ErrorCode.AUTH_INVALID);
  }

  @Test
  @DisplayName("check-email 은 미가입 이메일에 true 를 준다")
  void emailAvailability() {
    when(profileRepository.existsByEmail("free@b.com")).thenReturn(false);
    when(profileRepository.existsByEmail("taken@b.com")).thenReturn(true);

    assertThat(authService.isEmailAvailable("Free@B.com")).isTrue();
    assertThat(authService.isEmailAvailable("TAKEN@b.com")).isFalse();
  }

  private Profile existingProfile() {
    return Profile.signup("a@b.com", passwordEncoder.encode("secret1"), "kim");
  }
}
