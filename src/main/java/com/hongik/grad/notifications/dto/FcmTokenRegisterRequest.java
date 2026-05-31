package com.hongik.grad.notifications.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** FCM token registration body — spec §6-4. */
public record FcmTokenRegisterRequest(
    @JsonProperty("device_token") @NotBlank String deviceToken,
    @JsonProperty("platform") @NotBlank @Pattern(regexp = "ios|android") String platform) {}
