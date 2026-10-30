package com.hongik.grad.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hongik.grad.auth.provider.JwtClaims;
import com.hongik.grad.auth.provider.JwtProvider;
import com.hongik.grad.auth.provider.JwtVerifier;
import com.hongik.grad.common.api.ErrorCode;
import com.hongik.grad.common.exception.DomainException;
import com.hongik.grad.support.JwtTestFactory;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Replaces the former SupabaseJwtVerifierTest: tokens are now minted and verified locally. */
class JwtVerifierTest {

  private final JwtVerifier verifier = new JwtVerifier(JwtTestFactory.TEST_SECRET);
  private final JwtProvider provider =
      new JwtProvider(JwtTestFactory.TEST_SECRET, Duration.ofHours(1), Duration.ofDays(30));

  @Test
  @DisplayName("유효한 액세스 토큰은 sub UUID와 email 클레임을 반환한다")
  void verifyValid() {
    UUID userId = UUID.randomUUID();

    JwtClaims claims = verifier.verify(provider.createAccessToken(userId, "user@example.com"));

    assertThat(claims.sub()).isEqualTo(userId);
    assertThat(claims.email()).isEqualTo("user@example.com");
    assertThat(claims.audience()).isEqualTo("authenticated");
  }

  @Test
  @DisplayName("만료된 토큰은 AUTH_EXPIRED 로 거절한다")
  void verifyExpired() {
    assertThatThrownBy(() -> verifier.verify(JwtTestFactory.expiredToken(UUID.randomUUID())))
        .isInstanceOf(DomainException.class)
        .extracting(e -> ((DomainException) e).code())
        .isEqualTo(ErrorCode.AUTH_EXPIRED);
  }

  @Test
  @DisplayName("다른 키로 서명된 토큰은 AUTH_INVALID 로 거절한다")
  void verifyForged() {
    assertThatThrownBy(() -> verifier.verify(JwtTestFactory.forgedToken(UUID.randomUUID())))
        .isInstanceOf(DomainException.class)
        .extracting(e -> ((DomainException) e).code())
        .isEqualTo(ErrorCode.AUTH_INVALID);
  }

  @Test
  @DisplayName("audience 가 authenticated 가 아니면 거절한다")
  void verifyWrongAudience() {
    assertThatThrownBy(() -> verifier.verify(JwtTestFactory.anonToken(UUID.randomUUID())))
        .isInstanceOf(DomainException.class)
        .extracting(e -> ((DomainException) e).code())
        .isEqualTo(ErrorCode.AUTH_INVALID);
  }

  @Test
  @DisplayName("리프레시 토큰으로는 요청을 인증할 수 없다")
  void refreshTokenCannotAuthenticate() {
    String refresh = provider.createRefreshToken(UUID.randomUUID());

    assertThatThrownBy(() -> verifier.verify(refresh))
        .isInstanceOf(DomainException.class)
        .extracting(e -> ((DomainException) e).code())
        .isEqualTo(ErrorCode.AUTH_INVALID);
  }

  @Test
  @DisplayName("액세스 토큰으로는 재발급을 받을 수 없다")
  void accessTokenCannotRefresh() {
    String access = provider.createAccessToken(UUID.randomUUID(), "a@b.com");

    assertThatThrownBy(() -> verifier.verifyRefresh(access))
        .isInstanceOf(DomainException.class)
        .extracting(e -> ((DomainException) e).code())
        .isEqualTo(ErrorCode.AUTH_INVALID);
  }

  @Test
  @DisplayName("리프레시 토큰은 verifyRefresh 로 통과한다")
  void verifyRefreshValid() {
    UUID userId = UUID.randomUUID();

    assertThat(verifier.verifyRefresh(provider.createRefreshToken(userId)).sub()).isEqualTo(userId);
  }

  @Test
  @DisplayName("비밀키가 32바이트 미만이면 기동 시점에 실패한다")
  void rejectsShortSecret() {
    assertThatThrownBy(() -> new JwtVerifier("too-short"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("at least 32 bytes");
  }

  @Test
  @DisplayName("비밀키가 placeholder 이면 기동 시점에 실패한다")
  void rejectsPlaceholderSecret() {
    assertThatThrownBy(() -> new JwtVerifier("replace-me-with-a-random-32-byte-secret"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("placeholder");
  }
}
