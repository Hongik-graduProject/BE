package com.hongik.grad.notifications.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/** FCM token deletion body — spec §6-5. */
public record FcmTokenDeleteRequest(@JsonProperty("device_token") @NotBlank String deviceToken) {}
