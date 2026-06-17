package com.hongik.grad.auth.dto;

/** check-email result: {@code available} is true when the email is NOT yet registered. */
public record EmailAvailabilityResponse(boolean available) {}
