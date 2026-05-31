package com.hongik.grad.dev;

import com.fasterxml.jackson.databind.JsonNode;
import com.hongik.grad.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LOCAL DEV ONLY — never enabled in prod.
 *
 * <p>Proxies Supabase GoTrue signup/login so the API can be tested from Swagger without the
 * frontend SDK. Real Supabase users are created and real access tokens are returned (copy
 * {@code data.access_token} into the Authorize dialog). The {@link Profile @Profile("local")} guard
 * keeps these out of prod, where signup/login belong to the frontend + Supabase SDK.
 */
@RestController
@RequestMapping("/dev/auth")
@Profile("local")
public class DevAuthController {

  private static final Logger log = LoggerFactory.getLogger(DevAuthController.class);

  private final SupabaseAuthClient supabaseAuthClient;

  public DevAuthController(SupabaseAuthClient supabaseAuthClient) {
    this.supabaseAuthClient = supabaseAuthClient;
  }

  /** Sign a new user up in Supabase. Returns the GoTrue response (user + session if confirmed). */
  @PostMapping("/signup")
  public ApiResponse<JsonNode> signUp(@Valid @RequestBody DevAuthRequest request) {
    log.info("DEV signup proxy for email={}", request.email());
    return ApiResponse.success(supabaseAuthClient.signUp(request.email(), request.password()));
  }

  /** Log in to Supabase. Returns the GoTrue token response (access_token, refresh_token, user). */
  @PostMapping("/login")
  public ApiResponse<JsonNode> login(@Valid @RequestBody DevAuthRequest request) {
    log.info("DEV login proxy for email={}", request.email());
    return ApiResponse.success(
        supabaseAuthClient.signInWithPassword(request.email(), request.password()));
  }
}
