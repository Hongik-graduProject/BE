package com.hongik.grad.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Public token-refresh request. */
public record RefreshRequest(@NotBlank String refreshToken) {}
