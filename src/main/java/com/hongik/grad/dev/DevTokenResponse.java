package com.hongik.grad.dev;

/** LOCAL DEV ONLY — minted token payload returned to the caller. */
public record DevTokenResponse(String accessToken, String tokenType, String sub, long expiresIn) {}
