package com.hongik.grad.analysis.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hongik.grad.analysis.application.AnalysisQueryService;
import com.hongik.grad.analysis.application.ReportBackfillService;
import com.hongik.grad.analysis.application.ReportCritiqueService;
import com.hongik.grad.analysis.dto.AppliedThemeBriefResponse;
import com.hongik.grad.analysis.dto.AppliedThemeResponse;
import com.hongik.grad.analysis.dto.PaginationMeta;
import com.hongik.grad.analysis.dto.ReportDetailResponse;
import com.hongik.grad.analysis.dto.ReportListItemResponse;
import com.hongik.grad.analysis.dto.ReportListResponse;
import com.hongik.grad.analysis.dto.SensitiveMetricResponse;
import com.hongik.grad.analysis.dto.ThemeItemResponse;
import com.hongik.grad.analysis.dto.ThemesResponse;
import com.hongik.grad.auth.filter.JwtAuthenticationFilter;
import com.hongik.grad.auth.handler.JsonAccessDeniedHandler;
import com.hongik.grad.auth.handler.JsonAuthenticationEntryPoint;
import com.hongik.grad.auth.provider.JwtVerifier;
import com.hongik.grad.common.api.ErrorCode;
import com.hongik.grad.common.exception.DomainException;
import com.hongik.grad.config.SecurityConfig;
import com.hongik.grad.support.JwtTestFactory;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AnalysisController.class)
@Import({
  SecurityConfig.class,
  JwtVerifier.class,
  JwtAuthenticationFilter.class,
  JsonAuthenticationEntryPoint.class,
  JsonAccessDeniedHandler.class
})
@ActiveProfiles("test")
class AnalysisControllerSliceTest {

  @Autowired private MockMvc mockMvc;
  @MockBean private AnalysisQueryService analysisQueryService;
  @MockBean private ReportBackfillService reportBackfillService;
  @MockBean private ReportCritiqueService reportCritiqueService;

  private static final UUID USER_ID = UUID.randomUUID();
  private static final String TOKEN =
      "Bearer " + JwtTestFactory.validToken(USER_ID, "test@test.com");

  @Test
  @DisplayName("GET /analysis/{ticker}/themes — stock+top3 정상 → 200 + themes 배열")
  void getThemes_success() throws Exception {
    ThemesResponse response =
        new ThemesResponse(
            "005930",
            "삼성전자",
            List.of(
                new ThemeItemResponse(
                    1L,
                    "AI/반도체",
                    1,
                    0.85,
                    true,
                    List.of(new SensitiveMetricResponse("per", "주가수익비율", 0.35, 1)))),
            "themesense-stage1-v1.0",
            OffsetDateTime.now(),
            null);
    given(analysisQueryService.getThemes("005930")).willReturn(response);

    mockMvc
        .perform(get("/analysis/005930/themes").header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("success"))
        .andExpect(jsonPath("$.data.ticker").value("005930"))
        .andExpect(jsonPath("$.data.name").value("삼성전자"))
        .andExpect(jsonPath("$.data.themes[0].id").value(1))
        .andExpect(jsonPath("$.data.themes[0].is_primary").value(true))
        .andExpect(jsonPath("$.data.themes[0].top_sensitive_metrics[0].code").value("per"));
  }

  @Test
  @DisplayName("GET /analysis/{ticker}/themes — stock+universe외 → 200 + themes:[] + message")
  void getThemes_universeOutside_returnsMessage() throws Exception {
    ThemesResponse response =
        new ThemesResponse(
            "999999", "카카오", List.of(), null, null, "ThemeSense 학습 universe 외 종목 — 테마 분류 대기 중");
    given(analysisQueryService.getThemes("999999")).willReturn(response);

    mockMvc
        .perform(get("/analysis/999999/themes").header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.themes.length()").value(0))
        .andExpect(jsonPath("$.data.message").exists());
  }

  @Test
  @DisplayName("GET /analysis/{ticker}/themes — stock 미존재 → 404")
  void getThemes_stockNotFound_404() throws Exception {
    given(analysisQueryService.getThemes("000000"))
        .willThrow(new DomainException(ErrorCode.NOT_FOUND, "종목을 찾을 수 없습니다: 000000"));

    mockMvc
        .perform(get("/analysis/000000/themes").header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("GET /analysis/{ticker}/reports — 성공 시 200 + reports + pagination")
  void listReports_success() throws Exception {
    ReportListItemResponse item =
        new ReportListItemResponse(
            10L,
            "사업보고서",
            "요약",
            new AppliedThemeBriefResponse(1L, "AI/반도체"),
            0.42,
            false,
            OffsetDateTime.now());
    ReportListResponse response =
        new ReportListResponse("005930", "삼성전자", List.of(item), PaginationMeta.of(1, 20, 1));
    given(
            analysisQueryService.listReports(
                eq(USER_ID), eq("005930"), anyInt(), anyInt(), anyString()))
        .willReturn(response);

    mockMvc
        .perform(get("/analysis/005930/reports").header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.ticker").value("005930"))
        .andExpect(jsonPath("$.data.name").value("삼성전자"))
        .andExpect(jsonPath("$.data.reports[0].id").value(10))
        .andExpect(jsonPath("$.data.reports[0].applied_theme.name").value("AI/반도체"))
        .andExpect(jsonPath("$.data.reports[0].expected_car").value(0.42))
        .andExpect(jsonPath("$.data.reports[0].is_read").value(false))
        .andExpect(jsonPath("$.data.pagination.total_count").value(1));
  }

  @Test
  @DisplayName("GET /analysis/{ticker}/reports — uppercase sort=LATEST → 200")
  void listReports_uppercaseSort_200() throws Exception {
    ReportListResponse response =
        new ReportListResponse("005930", "삼성전자", List.of(), PaginationMeta.of(1, 20, 0));
    given(
            analysisQueryService.listReports(
                eq(USER_ID), eq("005930"), anyInt(), anyInt(), eq("LATEST")))
        .willReturn(response);

    mockMvc
        .perform(
            get("/analysis/005930/reports")
                .param("sort", "LATEST")
                .header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.ticker").value("005930"));
  }

  @Test
  @DisplayName("GET /analysis/{ticker}/reports — invalid sort → 400")
  void listReports_invalidSort_400() throws Exception {
    given(analysisQueryService.listReports(any(), anyString(), anyInt(), anyInt(), eq("bad")))
        .willThrow(new DomainException(ErrorCode.INVALID_PARAM, "sort must be latest|oldest"));

    mockMvc
        .perform(
            get("/analysis/005930/reports")
                .param("sort", "bad")
                .header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_PARAM"));
  }

  @Test
  @DisplayName("GET /analysis/{ticker}/reports — page=0 → 400")
  void listReports_pageZero_400() throws Exception {
    given(analysisQueryService.listReports(any(), anyString(), eq(0), anyInt(), anyString()))
        .willThrow(new DomainException(ErrorCode.INVALID_PARAM, "page must be >= 1"));

    mockMvc
        .perform(
            get("/analysis/005930/reports")
                .param("page", "0")
                .header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_PARAM"));
  }

  @Test
  @DisplayName("GET /analysis/{ticker}/reports — 토큰 없으면 401")
  void listReports_noToken_401() throws Exception {
    mockMvc.perform(get("/analysis/005930/reports")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /analysis/reports/{report_id} — 성공 시 200 + is_read=true + spec keys")
  void getReportDetail_success() throws Exception {
    ReportDetailResponse response =
        new ReportDetailResponse(
            10L,
            "005930",
            "삼성전자",
            "사업보고서",
            new AppliedThemeResponse(1L, "AI/반도체", 0.85),
            null,
            "요약",
            0.42,
            -1.58,
            2.42,
            List.of(),
            List.of(),
            null,
            "themesense-stage1-v1.0",
            "themesense-stage2-v1.0",
            true,
            OffsetDateTime.now());
    given(analysisQueryService.getReportDetail(eq(USER_ID), eq(10L))).willReturn(response);

    mockMvc
        .perform(get("/analysis/reports/10").header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(10))
        .andExpect(jsonPath("$.data.ticker").value("005930"))
        .andExpect(jsonPath("$.data.name").value("삼성전자"))
        .andExpect(jsonPath("$.data.is_read").value(true))
        .andExpect(jsonPath("$.data.applied_theme.id").value(1));
  }

  @Test
  @DisplayName("GET /analysis/reports/{report_id} — 없는 보고서 → 404 NOT_FOUND")
  void getReportDetail_notFound_404() throws Exception {
    given(analysisQueryService.getReportDetail(any(), eq(999L)))
        .willThrow(new DomainException(ErrorCode.NOT_FOUND, "보고서를 찾을 수 없습니다"));

    mockMvc
        .perform(get("/analysis/reports/999").header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }
}
