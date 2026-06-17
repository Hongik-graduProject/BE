package com.hongik.grad.auth.dto;

/**
 * Account deletion payload. The frontend sends {@code password} for UX symmetry; the BE relies on
 * the authenticated access token to identify the caller, so the field is accepted but unused.
 */
public record DeleteAccountRequest(String password) {}
