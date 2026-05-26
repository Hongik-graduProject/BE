package com.hongik.grad.auth.provider;

import java.time.Instant;
import java.util.UUID;

/**
 * Verified Supabase JWT claims, narrowed to the fields the application cares about.
 *
 * <p>Supabase issues HS256-signed tokens whose {@code sub} is the {@code auth.users.id} UUID and
 * whose {@code aud} must be {@code "authenticated"} for a logged-in (non-anonymous) user.
 */
public record JwtClaims(UUID sub, String email, String audience, Instant expiresAt) {}
