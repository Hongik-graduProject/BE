package com.hongik.grad.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Public signup credentials. nickname is optional (onboarding stores the canonical profile). */
public record SignupRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 6) String password,
    @Size(max = 50) String nickname) {}
