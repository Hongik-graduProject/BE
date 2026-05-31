package com.hongik.grad.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.hongik.grad.auth.provider.JwtClaims;
import com.hongik.grad.auth.provider.SupabaseJwtVerifier;
import com.hongik.grad.common.api.ApiResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DevTokenControllerTest {

  private static final String SECRET = "dev-token-test-secret-32-bytes-minimum-aaaa";

  private final DevTokenController controller = new DevTokenController(SECRET);
  private final SupabaseJwtVerifier verifier = new SupabaseJwtVerifier(SECRET);

  @Test
  void mintsTokenThatPassesSupabaseVerification() {
    UUID sub = UUID.randomUUID();
    ApiResponse<DevTokenResponse> response =
        controller.mint(new DevTokenRequest("dev@local.test", sub.toString()));

    DevTokenResponse body = response.data();
    assertThat(body.tokenType()).isEqualTo("Bearer");
    assertThat(body.sub()).isEqualTo(sub.toString());
    assertThat(body.expiresIn()).isEqualTo(3600);

    JwtClaims claims = verifier.verify(body.accessToken());
    assertThat(claims.sub()).isEqualTo(sub);
    assertThat(claims.email()).isEqualTo("dev@local.test");
    assertThat(claims.audience()).isEqualTo("authenticated");
  }

  @Test
  void generatesRandomSubWhenRequestOmitted() {
    ApiResponse<DevTokenResponse> response = controller.mint(null);
    JwtClaims claims = verifier.verify(response.data().accessToken());
    assertThat(claims.sub()).isNotNull();
  }
}
