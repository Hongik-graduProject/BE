package com.hongik.grad.auth.provider;

import com.hongik.grad.common.api.ErrorCode;
import com.hongik.grad.common.exception.DomainException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Verifies Supabase-issued access tokens.
 *
 * <p>Supabase projects sign access tokens with <strong>asymmetric JWT signing keys</strong>
 * (ES256/RS256) published at {@code <project>/auth/v1/.well-known/jwks.json}. This verifier resolves
 * the signing key by the token's {@code kid} header from that JWKS (fetched lazily and cached, with a
 * throttled refresh on key rotation). The backend never issues these tokens — it only verifies
 * signature, expiry, and audience. The audience is pinned to {@code "authenticated"} so {@code anon}
 * tokens are rejected.
 *
 * <p>Tokens without a {@code kid} header fall back to the legacy HS256 shared secret
 * ({@code supabase.jwt-secret}). This keeps the LOCAL-only {@code DevTokenController} mint flow
 * (Swagger) working, since those tokens are HMAC-signed with that same secret.
 */
@Component
public class SupabaseJwtVerifier {

  private static final Logger log = LoggerFactory.getLogger(SupabaseJwtVerifier.class);
  private static final String REQUIRED_AUDIENCE = "authenticated";
  private static final String JWKS_PATH = "/auth/v1/.well-known/jwks.json";
  private static final long MIN_REFRESH_INTERVAL_MS = 60_000L;

  private final SecretKey hsKey;
  private final WebClient jwksClient;

  private volatile Map<String, Key> keysByKid = Map.of();
  private volatile long lastFetchEpochMs = 0L;

  public SupabaseJwtVerifier(
      @Value("${supabase.jwt-secret}") String secret, @Value("${supabase.url:}") String url) {
    if (secret == null || secret.isBlank() || secret.startsWith("replace-me")) {
      throw new IllegalStateException(
          "supabase.jwt-secret is unset or still a placeholder. Set SUPABASE_JWT_SECRET to the"
              + " Supabase project JWT secret.");
    }
    this.hsKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.jwksClient =
        (url == null || url.isBlank()) ? null : WebClient.builder().baseUrl(url).build();
    // Best-effort warm so the first authenticated request doesn't pay the JWKS fetch latency. A
    // failure here is non-fatal: the cache lazily refreshes on the first verify().
    refreshJwks();
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
              .keyLocator(keyLocator())
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

  /**
   * Resolves the verification key from the JWS header: by {@code kid} against the cached JWKS
   * (asymmetric Supabase tokens), or the HS256 shared secret for {@code kid}-less dev tokens.
   */
  private Locator<Key> keyLocator() {
    return header -> {
      Object kid = header.get("kid");
      if (kid == null) {
        return hsKey;
      }
      Key key = keysByKid.get(kid.toString());
      if (key == null) {
        refreshJwks();
        key = keysByKid.get(kid.toString());
      }
      if (key == null) {
        throw new DomainException(ErrorCode.AUTH_INVALID, "Unknown signing key");
      }
      return key;
    };
  }

  /** Fetches and caches the project JWKS. Throttled and best-effort: never throws. */
  private synchronized void refreshJwks() {
    if (jwksClient == null) {
      return;
    }
    long now = System.currentTimeMillis();
    if (!keysByKid.isEmpty() && now - lastFetchEpochMs < MIN_REFRESH_INTERVAL_MS) {
      return;
    }
    try {
      String json =
          jwksClient
              .get()
              .uri(JWKS_PATH)
              .retrieve()
              .bodyToMono(String.class)
              .block(Duration.ofSeconds(5));
      JwkSet jwkSet = Jwks.setParser().build().parse(json);
      Map<String, Key> resolved = new HashMap<>();
      for (Jwk<?> jwk : jwkSet.getKeys()) {
        if (jwk.getId() != null) {
          resolved.put(jwk.getId(), jwk.toKey());
        }
      }
      this.keysByKid = Map.copyOf(resolved);
      this.lastFetchEpochMs = now;
      log.info("Loaded {} Supabase JWKS signing key(s)", resolved.size());
    } catch (Exception ex) {
      log.warn("Failed to refresh Supabase JWKS: {}", ex.getMessage());
    }
  }
}
