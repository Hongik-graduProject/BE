package com.hongik.grad.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Public login credentials. */
public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
