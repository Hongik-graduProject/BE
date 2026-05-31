package com.hongik.grad.dev;

/**
 * LOCAL DEV ONLY — request body for the dev token mint endpoint. Both fields are optional: a
 * missing {@code sub} yields a random UUID, a missing {@code email} yields a synthetic dev address.
 */
public record DevTokenRequest(String email, String sub) {}
