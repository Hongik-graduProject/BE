package com.hongik.grad.auth.provider;

import com.hongik.grad.common.api.ErrorCode;
import com.hongik.grad.common.exception.DomainException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifies the HS256 tokens this backend issues ({@link JwtProvider}).
 *
 * <p>There is no external identity provider: signature, expiry and audience are all checked against
 * the local {@code jwt.secret}. {@link #verify} accepts only access tokens and {@link
 * #verifyRefresh} only refresh tokens, so neither can stand in for the other.
 */
@Component
public class JwtVerifier {

  private static final Logger log = LoggerFactory.getLogger(JwtVerifier.class);
  private static final String REQUIRED_AUDIENCE = JwtProvider.AUDIENCE;
  private static final int MIN_SECRET_BYTES = 32;

  private final SecretKey key;

  public JwtVerifier(@Value("${jwt.secret}") String secret) {
    this.key = hmacKey(secret);
  }

  /**
   * Builds the HMAC key and fails fast on a missing or placeholder secret. HS256 needs at least 32
   * bytes; a shorter secret would make every signature trivially forgeable.
   */
  static SecretKey hmacKey(String secret) {
    if (secret == null || secret.isBlank() || secret.startsWith("replace-me")) {
      throw new IllegalStateException(
          "jwt.secret is unset or still a placeholder. Set JWT_SECRET to a random value of at"
              + " least 32 characters.");
    }
    byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < MIN_SECRET_BYTES) {
      throw new IllegalStateException(
          "jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes; got " + bytes.length);
    }
    return Keys.hmacShaKeyFor(bytes);
  }

  /**
   * Verifies an access token. Throws {@link DomainException} with {@code AUTH_EXPIRED} on expiry
   * and {@code AUTH_INVALID} on anything else, including a refresh token presented as an access
   * token.
   */
  public JwtClaims verify(String token) {
    Claims claims = parse(token);
    if (JwtProvider.TYPE_REFRESH.equals(claims.get(JwtProvider.TYPE_CLAIM, String.class))) {
      log.debug("Refresh token presented as an access token");
      throw new DomainException(ErrorCode.AUTH_INVALID, "Invalid access token");
    }
    return toClaims(claims);
  }

  /** Verifies a refresh token. Rejects an access token presented in its place. */
  public JwtClaims verifyRefresh(String token) {
    Claims claims = parse(token);
    if (!JwtProvider.TYPE_REFRESH.equals(claims.get(JwtProvider.TYPE_CLAIM, String.class))) {
      log.debug("Access token presented as a refresh token");
      throw new DomainException(ErrorCode.AUTH_INVALID, "Invalid refresh token");
    }
    return toClaims(claims);
  }

  private Claims parse(String token) {
    try {
      Jws<Claims> jws =
          Jwts.parser()
              .verifyWith(key)
              .requireAudience(REQUIRED_AUDIENCE)
              .build()
              .parseSignedClaims(token);
      return jws.getPayload();
    } catch (ExpiredJwtException ex) {
      log.debug("JWT expired");
      throw new DomainException(ErrorCode.AUTH_EXPIRED, "Token expired");
    } catch (JwtException | IllegalArgumentException ex) {
      log.debug("JWT verification failed: {}", ex.getMessage());
      throw new DomainException(ErrorCode.AUTH_INVALID, "Invalid token");
    }
  }

  private JwtClaims toClaims(Claims claims) {
    UUID sub;
    try {
      sub = UUID.fromString(claims.getSubject());
    } catch (IllegalArgumentException | NullPointerException ex) {
      log.debug("JWT sub claim is not a valid UUID: {}", claims.getSubject());
      throw new DomainException(ErrorCode.AUTH_INVALID, "Invalid token subject");
    }
    String audience =
        claims.getAudience() == null || claims.getAudience().isEmpty()
            ? null
            : claims.getAudience().iterator().next();
    return new JwtClaims(
        sub,
        claims.get("email", String.class),
        audience,
        claims.getExpiration() == null ? null : claims.getExpiration().toInstant());
  }
}
