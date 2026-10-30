package com.hongik.grad.auth.dto;

/**
 * User block embedded in auth responses.
 *
 * <p>{@code id} is the profile UUID rendered as a string. The Flutter {@code User} model currently
 * types this as int; Phase 2 must widen it to String.
 */
public record AuthUserDto(String id, String email, String nickname) {}
