package com.hongik.grad.radar.api;

import com.hongik.grad.auth.principal.AuthUser;
import com.hongik.grad.common.api.ApiResponse;
import com.hongik.grad.radar.application.RadarEmitterRegistry;
import com.hongik.grad.radar.application.RadarService;
import com.hongik.grad.radar.dto.RadarPoint;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/radar")
public class RadarController {

  private final RadarService radarService;
  private final RadarEmitterRegistry registry;

  public RadarController(RadarService radarService, RadarEmitterRegistry registry) {
    this.radarService = radarService;
    this.registry = registry;
  }

  // from/to stay String so a malformed value becomes INVALID_PARAM, not a 500 type-mismatch.
  @GetMapping("/points")
  public ApiResponse<List<RadarPoint>> points(
      @AuthenticationPrincipal AuthUser user,
      @RequestParam(value = "from", required = false) String from,
      @RequestParam(value = "to", required = false) String to) {
    return ApiResponse.success(radarService.points(user.id(), from, to));
  }

  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@AuthenticationPrincipal AuthUser user) {
    return registry.connect(user.id());
  }
}
