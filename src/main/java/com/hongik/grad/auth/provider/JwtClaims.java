package com.hongik.grad.auth.provider;

import java.time.Instant;
import java.util.UUID;

/**
 * Verified JWT claims, narrowed to the fields the application cares about.
 *
 * <p>{@code sub} is the {@code profiles.id} UUID and {@code aud} must be {@code "authenticated"}.
 */
public record JwtClaims(UUID sub, String email, String audience, Instant expiresAt) {}
