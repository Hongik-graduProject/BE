package com.hongik.grad.analysis.api;

import com.hongik.grad.analysis.application.AnalysisQueryService;
import com.hongik.grad.analysis.dto.ReportDetailResponse;
import com.hongik.grad.analysis.dto.ReportListResponse;
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

  public AnalysisController(AnalysisQueryService analysisQueryService) {
    this.analysisQueryService = analysisQueryService;
  }

  @GetMapping("/{ticker}/themes")
  public ApiResponse<ThemesResponse> getThemes(
      @AuthenticationPrincipal AuthUser user, @PathVariable("ticker") String ticker) {
    log.debug("getThemes ticker={} user={}", ticker, user.id());
    return ApiResponse.success(analysisQueryService.getThemes(ticker));
  }

  @GetMapping("/{ticker}/reports")
  public ApiResponse<ReportListResponse> listReports(
      @AuthenticationPrincipal AuthUser user,
      @PathVariable("ticker") String ticker,
      @RequestParam(value = "page", defaultValue = "1") int page,
      @RequestParam(value = "size", defaultValue = "20") int size,
      @RequestParam(value = "sort", defaultValue = "latest") String sort) {
    return ApiResponse.success(
        analysisQueryService.listReports(user.id(), ticker, page, size, sort));
  }

  @GetMapping("/reports/{report_id}")
  public ApiResponse<ReportDetailResponse> getReportDetail(
      @AuthenticationPrincipal AuthUser user, @PathVariable("report_id") Long reportId) {
    return ApiResponse.success(analysisQueryService.getReportDetail(user.id(), reportId));
  }
}
