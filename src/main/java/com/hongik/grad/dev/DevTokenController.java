package com.hongik.grad.dev;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.hongik.grad.common.api.ApiResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LOCAL DEV ONLY — never enabled in prod.
 *
 * <p>This is a backdoor that mints arbitrary Supabase-shaped HS256 access tokens so Swagger UI can
 * exercise protected endpoints without a real Supabase login. The {@link Profile @Profile("local")}
 * guard is the sole gate: the bean is created only under the {@code local} profile, so any other
 * profile (notably {@code prod}) has no handler and {@code POST /dev/auth/token} returns 404.
 *
 * <p>Tokens are signed with the same {@code supabase.jwt-secret} that {@code SupabaseJwtVerifier}
 * verifies against, so under {@code local} (where that secret is a fixed dev value) the minted
 * token passes verification.
 */
@RestController
@RequestMapping("/dev/auth")
@Profile("local")
public class DevTokenController {

  private static final Logger log = LoggerFactory.getLogger(DevTokenController.class);
  private static final String REQUIRED_AUDIENCE = "authenticated";
  private static final long TTL_SECONDS = 3600;

  private final SecretKey key;

  public DevTokenController(@Value("${supabase.jwt-secret}") String secret) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(UTF_8));
  }

  @PostMapping("/token")
  public ApiResponse<DevTokenResponse> mint(
      @RequestBody(required = false) DevTokenRequest request) {
    UUID sub = resolveSub(request);
    String email =
        request != null && request.email() != null && !request.email().isBlank()
            ? request.email()
            : "dev+" + sub + "@local.test";

    Instant now = Instant.now();
    String token =
        Jwts.builder()
            .subject(sub.toString())
            .audience()
            .add(REQUIRED_AUDIENCE)
            .and()
            .claim("email", email)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(TTL_SECONDS, ChronoUnit.SECONDS)))
            .signWith(key, Jwts.SIG.HS256)
            .compact();

    log.info("Minted LOCAL DEV token for sub={}", mask(sub));
    return ApiResponse.success(new DevTokenResponse(token, "Bearer", sub.toString(), TTL_SECONDS));
  }

  private UUID resolveSub(DevTokenRequest request) {
    if (request == null || request.sub() == null || request.sub().isBlank()) {
      return UUID.randomUUID();
    }
    return UUID.fromString(request.sub());
  }

  private static String mask(UUID sub) {
    String s = sub.toString();
    return s.substring(0, 8) + "-****";
  }
}
