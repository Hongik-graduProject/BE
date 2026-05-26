package com.hongik.grad.auth.principal;

import java.util.UUID;

/**
 * Authenticated principal placed in {@code SecurityContextHolder} after JWT verification.
 *
 * <p>Controllers receive it via {@code @AuthenticationPrincipal AuthUser user}.
 */
public record AuthUser(UUID id, String email) {}
