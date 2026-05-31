package com.hongik.grad.notifications.api;

import com.hongik.grad.auth.principal.AuthUser;
import com.hongik.grad.common.api.ApiResponse;
import com.hongik.grad.notifications.application.NotificationService;
import com.hongik.grad.notifications.dto.NotificationListResponse;
import com.hongik.grad.notifications.dto.ReadAllRequest;
import com.hongik.grad.notifications.dto.ReadAllResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

  private final NotificationService notificationService;

  public NotificationController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @GetMapping
  public ApiResponse<NotificationListResponse> list(
      @AuthenticationPrincipal AuthUser user,
      @RequestParam(value = "is_read", required = false) Boolean isRead,
      @RequestParam(value = "type", required = false) String type,
      @RequestParam(value = "page", defaultValue = "1") int page,
      @RequestParam(value = "size", defaultValue = "20") int size) {
    return ApiResponse.success(notificationService.list(user.id(), isRead, type, page, size));
  }

  @PatchMapping("/{notification_id}/read")
  public ApiResponse<Void> markRead(
      @AuthenticationPrincipal AuthUser user,
      @PathVariable("notification_id") Long notificationId) {
    notificationService.markRead(user.id(), notificationId);
    return ApiResponse.success(null, "알림이 읽음 처리되었습니다");
  }

  @PatchMapping("/read-all")
  public ApiResponse<ReadAllResponse> markAllRead(
      @AuthenticationPrincipal AuthUser user,
      @RequestBody(required = false) ReadAllRequest request) {
    String ticker = request == null ? null : request.ticker();
    int readCount = notificationService.markAllRead(user.id(), ticker);
    return ApiResponse.success(new ReadAllResponse(readCount), readCount + "개의 알림이 읽음 처리되었습니다");
  }
}
