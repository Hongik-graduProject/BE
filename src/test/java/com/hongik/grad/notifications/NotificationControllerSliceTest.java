package com.hongik.grad.notifications;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongik.grad.analysis.dto.PaginationMeta;
import com.hongik.grad.auth.filter.SupabaseJwtAuthenticationFilter;
import com.hongik.grad.auth.handler.JsonAccessDeniedHandler;
import com.hongik.grad.auth.handler.JsonAuthenticationEntryPoint;
import com.hongik.grad.auth.provider.SupabaseJwtVerifier;
import com.hongik.grad.common.api.ErrorCode;
import com.hongik.grad.common.exception.DomainException;
import com.hongik.grad.config.SecurityConfig;
import com.hongik.grad.notifications.api.NotificationController;
import com.hongik.grad.notifications.application.NotificationService;
import com.hongik.grad.notifications.dto.NotificationItemResponse;
import com.hongik.grad.notifications.dto.NotificationListResponse;
import com.hongik.grad.support.JwtTestFactory;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
@Import({
  SecurityConfig.class,
  SupabaseJwtVerifier.class,
  SupabaseJwtAuthenticationFilter.class,
  JsonAuthenticationEntryPoint.class,
  JsonAccessDeniedHandler.class
})
@ActiveProfiles("test")
class NotificationControllerSliceTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockBean private NotificationService notificationService;

  private static final UUID USER_ID = UUID.randomUUID();
  private static final String TOKEN =
      "Bearer " + JwtTestFactory.validToken(USER_ID, "test@test.com");

  @Test
  @DisplayName("GET /notifications — 성공 시 200 + notifications + unread_count + pagination")
  void list_success() throws Exception {
    NotificationItemResponse item =
        new NotificationItemResponse(
            1001L,
            "disclosure_report",
            "삼성전자 새 분석 보고서",
            "본문",
            "005930",
            "삼성전자",
            501L,
            null,
            false,
            OffsetDateTime.now());
    NotificationListResponse response =
        new NotificationListResponse(List.of(item), 5L, PaginationMeta.of(1, 20, 1));
    given(notificationService.list(eq(USER_ID), any(), any(), anyInt(), anyInt()))
        .willReturn(response);

    mockMvc
        .perform(get("/notifications").header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.notifications[0].id").value(1001))
        .andExpect(jsonPath("$.data.notifications[0].type").value("disclosure_report"))
        .andExpect(jsonPath("$.data.notifications[0].stock_name").value("삼성전자"))
        .andExpect(jsonPath("$.data.notifications[0].report_id").value(501))
        .andExpect(jsonPath("$.data.unread_count").value(5))
        .andExpect(jsonPath("$.data.pagination.total_count").value(1));
  }

  @Test
  @DisplayName("GET /notifications — page=0 → 400")
  void list_invalidPage_400() throws Exception {
    given(notificationService.list(any(), any(), any(), eq(0), anyInt()))
        .willThrow(new DomainException(ErrorCode.INVALID_PARAM, "page must be >= 1"));

    mockMvc
        .perform(get("/notifications").param("page", "0").header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_PARAM"));
  }

  @Test
  @DisplayName("PATCH /notifications/{id}/read — 성공 시 200 + message")
  void markRead_success() throws Exception {
    mockMvc
        .perform(patch("/notifications/1001/read").header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("success"))
        .andExpect(jsonPath("$.message").value("알림이 읽음 처리되었습니다"));
  }

  @Test
  @DisplayName("PATCH /notifications/{id}/read — 타 user 소유 → 404")
  void markRead_notOwned_404() throws Exception {
    doThrow(new DomainException(ErrorCode.NOT_FOUND, "알림을 찾을 수 없습니다"))
        .when(notificationService)
        .markRead(eq(USER_ID), eq(999L));

    mockMvc
        .perform(patch("/notifications/999/read").header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("PATCH /notifications/read-all — body 없이 전체 읽음 → 200 + read_count")
  void markAllRead_noBody_success() throws Exception {
    given(notificationService.markAllRead(eq(USER_ID), isNull())).willReturn(5);

    mockMvc
        .perform(patch("/notifications/read-all").header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.read_count").value(5))
        .andExpect(jsonPath("$.message").value("5개의 알림이 읽음 처리되었습니다"));
  }

  @Test
  @DisplayName("PATCH /notifications/read-all — ticker filter → 200")
  void markAllRead_withTicker_success() throws Exception {
    given(notificationService.markAllRead(eq(USER_ID), eq("005930"))).willReturn(2);

    mockMvc
        .perform(
            patch("/notifications/read-all")
                .header(HttpHeaders.AUTHORIZATION, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ticker", "005930"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.read_count").value(2));
  }

  @Test
  @DisplayName("GET /notifications — 토큰 없으면 401")
  void list_noToken_401() throws Exception {
    mockMvc.perform(get("/notifications")).andExpect(status().isUnauthorized());
  }
}
