package com.hongik.grad.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OnboardingRequest(
    @NotBlank
        @Size(min = 2, max = 20)
        @Pattern(regexp = "^[가-힣a-zA-Z0-9]+$", message = "한글/영문/숫자만 사용할 수 있습니다")
        String nickname) {}
