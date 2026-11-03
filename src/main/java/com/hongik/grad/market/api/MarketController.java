package com.hongik.grad.market.api;

import com.hongik.grad.common.api.ApiResponse;
import com.hongik.grad.market.application.MarketSummaryService;
import com.hongik.grad.market.dto.MarketSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/market")
public class MarketController {

  private final MarketSummaryService marketSummaryService;

  public MarketController(MarketSummaryService marketSummaryService) {
    this.marketSummaryService = marketSummaryService;
  }

  @GetMapping("/summary")
  public ApiResponse<MarketSummaryResponse> summary() {
    return ApiResponse.success(marketSummaryService.summary());
  }
}
