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
 * Verifies Supabase-issued HS256 JWTs.
 *
 * <p>Supabase Auth signs access tokens with a project-wide HS256 secret. The backend never issues
 * tokens — it only verifies signature, expiry, and audience. The audience is pinned to {@code
 * "authenticated"} so {@code anon} tokens (used for unauthenticated Supabase client calls) are
 * rejected.
 */
@Component
public class SupabaseJwtVerifier {

  private static final Logger log = LoggerFactory.getLogger(SupabaseJwtVerifier.class);
  private static final String REQUIRED_AUDIENCE = "authenticated";

  private final SecretKey key;

  public SupabaseJwtVerifier(@Value("${supabase.jwt-secret}") String secret) {
    if (secret == null || secret.isBlank() || secret.startsWith("replace-me")) {
      throw new IllegalStateException(
          "supabase.jwt-secret is unset or still a placeholder. Set SUPABASE_JWT_SECRET to the"
              + " Supabase project JWT secret.");
    }
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Parses and verifies the compact-serialized JWT. Throws a {@link DomainException} with the
   * appropriate {@link ErrorCode} on any failure (expiry → {@code AUTH_EXPIRED}, anything else →
   * {@code AUTH_INVALID}).
   */
  public JwtClaims verify(String token) {
    try {
      Jws<Claims> jws =
          Jwts.parser()
              .verifyWith(key)
              .requireAudience(REQUIRED_AUDIENCE)
              .build()
              .parseSignedClaims(token);
      Claims claims = jws.getPayload();

      UUID sub;
      try {
        sub = UUID.fromString(claims.getSubject());
      } catch (IllegalArgumentException | NullPointerException ex) {
        log.debug("JWT sub claim is not a valid UUID: {}", claims.getSubject());
        throw new DomainException(ErrorCode.AUTH_INVALID, "Invalid token subject");
      }

      String email = claims.get("email", String.class);
      String audience =
          claims.getAudience() == null || claims.getAudience().isEmpty()
              ? null
              : claims.getAudience().iterator().next();
      return new JwtClaims(
          sub,
          email,
          audience,
          claims.getExpiration() == null ? null : claims.getExpiration().toInstant());
    } catch (ExpiredJwtException ex) {
      log.debug("JWT expired");
      throw new DomainException(ErrorCode.AUTH_EXPIRED, "Access token expired");
    } catch (DomainException ex) {
      throw ex;
    } catch (JwtException | IllegalArgumentException ex) {
      log.debug("JWT verification failed: {}", ex.getMessage());
      throw new DomainException(ErrorCode.AUTH_INVALID, "Invalid access token");
    }
  }
}
