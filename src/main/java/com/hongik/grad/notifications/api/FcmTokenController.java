package com.hongik.grad.notifications.api;

import com.hongik.grad.auth.principal.AuthUser;
import com.hongik.grad.common.api.ApiResponse;
import com.hongik.grad.notifications.application.FcmTokenService;
import com.hongik.grad.notifications.dto.FcmTokenDeleteRequest;
import com.hongik.grad.notifications.dto.FcmTokenRegisterRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications/fcm-token")
public class FcmTokenController {

  private final FcmTokenService fcmTokenService;

  public FcmTokenController(FcmTokenService fcmTokenService) {
    this.fcmTokenService = fcmTokenService;
  }

  @PostMapping
  public ApiResponse<Void> register(
      @AuthenticationPrincipal AuthUser user, @Valid @RequestBody FcmTokenRegisterRequest request) {
    fcmTokenService.register(user.id(), request.deviceToken(), request.platform());
    return ApiResponse.success(null, "디바이스 토큰이 등록되었습니다");
  }

  @DeleteMapping
  public ApiResponse<Void> delete(
      @AuthenticationPrincipal AuthUser user, @Valid @RequestBody FcmTokenDeleteRequest request) {
    fcmTokenService.delete(user.id(), request.deviceToken());
    return ApiResponse.success(null, "디바이스 토큰이 삭제되었습니다");
  }
}
