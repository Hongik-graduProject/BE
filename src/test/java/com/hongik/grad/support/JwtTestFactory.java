package com.hongik.grad.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

/**
 * Builds Supabase-shaped HS256 JWTs for tests. Mirrors the {@code supabase.jwt-secret} in {@code
 * application-test.yml} / {@code application-test-db.yml} so signature verification succeeds.
 */
public final class JwtTestFactory {

  public static final String TEST_SECRET = "test-secret-32-bytes-minimum-aaaaaaaa";
  private static final SecretKey KEY =
      Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
  private static final SecretKey WRONG_KEY =
      Keys.hmacShaKeyFor(
          "another-secret-32-bytes-minimum-bbbbbbbb".getBytes(StandardCharsets.UTF_8));

  private JwtTestFactory() {}

  public static String validToken(UUID sub, String email) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(sub.toString())
        .audience()
        .add("authenticated")
        .and()
        .claim("email", email)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(1, ChronoUnit.HOURS)))
        .signWith(KEY, Jwts.SIG.HS256)
        .compact();
  }

  public static String expiredToken(UUID sub) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(sub.toString())
        .audience()
        .add("authenticated")
        .and()
        .issuedAt(Date.from(now.minus(2, ChronoUnit.HOURS)))
        .expiration(Date.from(now.minus(1, ChronoUnit.HOURS)))
        .signWith(KEY, Jwts.SIG.HS256)
        .compact();
  }

  public static String anonToken(UUID sub) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(sub.toString())
        .audience()
        .add("anon")
        .and()
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(1, ChronoUnit.HOURS)))
        .signWith(KEY, Jwts.SIG.HS256)
        .compact();
  }

  public static String forgedToken(UUID sub) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(sub.toString())
        .audience()
        .add("authenticated")
        .and()
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(1, ChronoUnit.HOURS)))
        .signWith(WRONG_KEY, Jwts.SIG.HS256)
        .compact();
  }
}
