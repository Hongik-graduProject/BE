package com.hongik.grad.users.api;

import com.hongik.grad.auth.principal.AuthUser;
import com.hongik.grad.common.api.ApiResponse;
import com.hongik.grad.users.application.OnboardingService;
import com.hongik.grad.users.dto.OnboardingRequest;
import com.hongik.grad.users.dto.ProfileResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

  private final OnboardingService onboardingService;

  public UserController(OnboardingService onboardingService) {
    this.onboardingService = onboardingService;
  }

  @PostMapping
  public ApiResponse<ProfileResponse> onboard(
      @AuthenticationPrincipal AuthUser user, @Valid @RequestBody OnboardingRequest request) {
    return ApiResponse.success(onboardingService.onboard(user, request));
  }
}
