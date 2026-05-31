package com.hongik.grad.dev;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Credentials for the local-dev Supabase signup/login proxy. */
public record DevAuthRequest(
    @NotBlank @Email String email, @NotBlank @Size(min = 6) String password) {}
