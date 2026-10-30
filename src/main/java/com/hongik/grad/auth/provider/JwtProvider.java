package com.hongik.grad.auth.provider;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Mints the HS256 access and refresh tokens this backend issues at login.
 *
 * <p>Both tokens carry {@code aud=authenticated} so they pass the same audience check. The refresh
 * token is told apart by the {@code typ} claim, which {@link JwtVerifier} enforces: an access token
 * cannot be redeemed as a refresh token, and a refresh token cannot authenticate a request.
 *
 * <p>Refresh tokens are stateless (no DB row). Revocation therefore happens only through account
 * deletion, which {@code AuthService.refresh} detects by re-reading the profile.
 */
@Component
public class JwtProvider {

  public static final String AUDIENCE = "authenticated";
  public static final String TYPE_CLAIM = "typ";
  public static final String TYPE_REFRESH = "refresh";

  private final SecretKey key;
  private final Duration accessTtl;
  private final Duration refreshTtl;

  public JwtProvider(
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.access-token-ttl:PT1H}") Duration accessTtl,
      @Value("${jwt.refresh-token-ttl:P30D}") Duration refreshTtl) {
    this.key = JwtVerifier.hmacKey(secret);
    this.accessTtl = accessTtl;
    this.refreshTtl = refreshTtl;
  }

  public String createAccessToken(UUID userId, String email) {
    return build(userId, email, accessTtl, null);
  }

  public String createRefreshToken(UUID userId) {
    return build(userId, null, refreshTtl, TYPE_REFRESH);
  }

  /** Seconds until the access token expires, for the {@code expires_in} wire field. */
  public long accessTokenExpiresInSeconds() {
    return accessTtl.toSeconds();
  }

  private String build(UUID userId, String email, Duration ttl, String type) {
    Instant now = Instant.now();
    JwtBuilder builder =
        Jwts.builder()
            .subject(userId.toString())
            .audience()
            .add(AUDIENCE)
            .and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)));
    if (email != null) {
      builder.claim("email", email);
    }
    if (type != null) {
      builder.claim(TYPE_CLAIM, type);
    }
    return builder.signWith(key, Jwts.SIG.HS256).compact();
  }
}
