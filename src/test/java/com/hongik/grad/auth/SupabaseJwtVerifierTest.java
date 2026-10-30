package com.hongik.grad.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hongik.grad.auth.provider.JwtClaims;
import com.hongik.grad.auth.provider.SupabaseJwtVerifier;
import com.hongik.grad.common.api.ErrorCode;
import com.hongik.grad.common.exception.DomainException;
import com.hongik.grad.support.JwtTestFactory;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SupabaseJwtVerifierTest {

  // No Supabase URL: JwtTestFactory tokens carry no kid, so the verifier takes the HS256 path.
  private final SupabaseJwtVerifier verifier =
      new SupabaseJwtVerifier(JwtTestFactory.TEST_SECRET, "");

  @Test
  @DisplayName("유효한 토큰은 sub UUID와 email 클레임을 반환한다")
  void verifyValid() {
    UUID userId = UUID.randomUUID();
    String token = JwtTestFactory.validToken(userId, "user@example.com");

    JwtClaims claims = verifier.verify(token);

    assertThat(claims.sub()).isEqualTo(userId);
    assertThat(claims.email()).isEqualTo("user@example.com");
    assertThat(claims.audience()).isEqualTo("authenticated");
  }

  @Test
  @DisplayName("만료된 토큰은 AUTH_EXPIRED DomainException 을 던진다")
  void verifyExpired() {
    String token = JwtTestFactory.expiredToken(UUID.randomUUID());

    assertThatThrownBy(() -> verifier.verify(token))
        .isInstanceOf(DomainException.class)
        .extracting(ex -> ((DomainException) ex).code())
        .isEqualTo(ErrorCode.AUTH_EXPIRED);
  }

  @Test
  @DisplayName("위조 서명 토큰은 AUTH_INVALID DomainException 을 던진다")
  void verifyForgedSignature() {
    String token = JwtTestFactory.forgedToken(UUID.randomUUID());

    assertThatThrownBy(() -> verifier.verify(token))
        .isInstanceOf(DomainException.class)
        .extracting(ex -> ((DomainException) ex).code())
        .isEqualTo(ErrorCode.AUTH_INVALID);
  }

  @Test
  @DisplayName("aud=anon 토큰은 audience 불일치로 AUTH_INVALID 를 던진다")
  void verifyWrongAudience() {
    String token = JwtTestFactory.anonToken(UUID.randomUUID());

    assertThatThrownBy(() -> verifier.verify(token))
        .isInstanceOf(DomainException.class)
        .extracting(ex -> ((DomainException) ex).code())
        .isEqualTo(ErrorCode.AUTH_INVALID);
  }
}
