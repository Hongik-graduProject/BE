package com.hongik.grad.auth.api;

import com.hongik.grad.auth.application.AuthService;
import com.hongik.grad.auth.dto.DeleteAccountRequest;
import com.hongik.grad.auth.dto.EmailAvailabilityResponse;
import com.hongik.grad.auth.dto.LoginRequest;
import com.hongik.grad.auth.dto.RefreshRequest;
import com.hongik.grad.auth.dto.SignupRequest;
import com.hongik.grad.auth.dto.SignupResponse;
import com.hongik.grad.auth.dto.TokenResponse;
import com.hongik.grad.auth.principal.AuthUser;
import com.hongik.grad.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public + authenticated auth endpoints backed by the local {@code profiles} table. The frontend
 * calls these directly; the BE is the contract source of truth. Responses use the standard {@link
 * ApiResponse} wrapper.
 */
@RestController
@RequestMapping("/auth")
@Validated
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/signup")
  public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
    return ApiResponse.success(
        authService.signup(request.email(), request.password(), request.nickname()));
  }

  @PostMapping("/login")
  public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
    return ApiResponse.success(authService.login(request.email(), request.password()));
  }

  @PostMapping("/refresh")
  public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
    return ApiResponse.success(authService.refresh(request.refreshToken()));
  }

  @PostMapping("/logout")
  public ApiResponse<Void> logout(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
    if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
      authService.logout(authorization.substring(7));
    }
    return ApiResponse.success(null, "로그아웃되었습니다");
  }

  @GetMapping("/check-email")
  public ApiResponse<EmailAvailabilityResponse> checkEmail(
      @RequestParam @NotBlank @Email String email) {
    return ApiResponse.success(new EmailAvailabilityResponse(authService.isEmailAvailable(email)));
  }

  @DeleteMapping("/account")
  public ApiResponse<Void> deleteAccount(
      @AuthenticationPrincipal AuthUser user,
      @RequestBody(required = false) DeleteAccountRequest request) {
    authService.deleteAccount(user.id());
    return ApiResponse.success(null, "회원 탈퇴가 완료되었습니다");
  }
}
