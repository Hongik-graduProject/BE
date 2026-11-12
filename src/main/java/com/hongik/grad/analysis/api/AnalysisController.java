package com.hongik.grad.analysis.api;

import com.hongik.grad.analysis.application.AnalysisQueryService;
import com.hongik.grad.analysis.application.ReportBackfillService;
import com.hongik.grad.analysis.application.ReportCritiqueService;
import com.hongik.grad.analysis.dto.CritiqueResponse;
import com.hongik.grad.analysis.dto.ReportDetailResponse;
import com.hongik.grad.analysis.dto.ReportListResponse;
import com.hongik.grad.analysis.dto.StockPredictionResponse;
import com.hongik.grad.analysis.dto.ThemesResponse;
import com.hongik.grad.auth.principal.AuthUser;
import com.hongik.grad.common.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analysis")
public class AnalysisController {

  private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);

  private final AnalysisQueryService analysisQueryService;
  private final ReportBackfillService reportBackfillService;
  private final ReportCritiqueService reportCritiqueService;

  public AnalysisController(
      AnalysisQueryService analysisQueryService,
      ReportBackfillService reportBackfillService,
      ReportCritiqueService reportCritiqueService) {
    this.analysisQueryService = analysisQueryService;
    this.reportBackfillService = reportBackfillService;
    this.reportCritiqueService = reportCritiqueService;
  }

  @GetMapping("/{ticker}/themes")
  public ApiResponse<ThemesResponse> getThemes(
      @AuthenticationPrincipal AuthUser user, @PathVariable("ticker") String ticker) {
    log.debug("getThemes ticker={} user={}", ticker, user.id());
    return ApiResponse.success(analysisQueryService.getThemes(ticker));
  }

  @GetMapping("/{ticker}/prediction")
  public ApiResponse<StockPredictionResponse> getPrediction(
      @AuthenticationPrincipal AuthUser user, @PathVariable("ticker") String ticker) {
    log.debug("getPrediction ticker={} user={}", ticker, user.id());
    return ApiResponse.success(analysisQueryService.getPrediction(ticker));
  }

  @GetMapping("/{ticker}/reports")
  public ApiResponse<ReportListResponse> listReports(
      @AuthenticationPrincipal AuthUser user,
      @PathVariable("ticker") String ticker,
      @RequestParam(value = "page", defaultValue = "1") int page,
      @RequestParam(value = "size", defaultValue = "20") int size,
      @RequestParam(value = "sort", defaultValue = "latest") String sort) {
    // 먼저 조회해서 파라미터·종목 검증을 끝낸다. 잘못된 요청에 DART 호출을 하지 않기 위해서다.
    ReportListResponse reports =
        analysisQueryService.listReports(user.id(), ticker, page, size, sort);
    if (page == 1 && reportBackfillService.ensureRecentReports(ticker)) {
      reports = analysisQueryService.listReports(user.id(), ticker, page, size, sort);
    }
    return ApiResponse.success(reports);
  }

  @GetMapping("/reports/{report_id}")
  public ApiResponse<ReportDetailResponse> getReportDetail(
      @AuthenticationPrincipal AuthUser user, @PathVariable("report_id") Long reportId) {
    return ApiResponse.success(analysisQueryService.getReportDetail(user.id(), reportId));
  }

  @GetMapping("/reports/{report_id}/critique")
  public ApiResponse<CritiqueResponse> getCritique(
      @AuthenticationPrincipal AuthUser user, @PathVariable("report_id") Long reportId) {
    log.debug("getCritique report={} user={}", reportId, user.id());
    return ApiResponse.success(reportCritiqueService.critique(reportId));
  }
}
