package com.hongik.grad.auth.dto;

/**
 * Session tokens. Wire shape (snake_case): {@code access_token}, {@code refresh_token}, {@code
 * token_type}, {@code expires_in}, {@code user}. Used by login (with user) and refresh.
 *
 * <p>{@code user} is null for refresh responses (GoTrue refresh omits the user block); it is
 * dropped from the payload by the global {@code NON_NULL} include.
 */
public record TokenResponse(
    String accessToken, String refreshToken, String tokenType, long expiresIn, AuthUserDto user) {}
