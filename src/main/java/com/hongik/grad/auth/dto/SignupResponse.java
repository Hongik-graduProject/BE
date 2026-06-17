package com.hongik.grad.auth.dto;

/**
 * Signup result. Field names match the Flutter {@code SignupResponse} (snake_case on the wire via
 * global Jackson strategy): {@code user_id}, {@code email}, {@code nickname}, {@code created_at}.
 *
 * <p>{@code userId} is the Supabase UUID as a string (Flutter currently types it int — Phase 2).
 */
public record SignupResponse(String userId, String email, String nickname, String createdAt) {}
