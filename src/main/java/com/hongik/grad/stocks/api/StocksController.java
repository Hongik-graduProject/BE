package com.hongik.grad.stocks.api;

import com.hongik.grad.auth.principal.AuthUser;
import com.hongik.grad.common.api.ApiResponse;
import com.hongik.grad.stocks.application.SearchHistoryService;
import com.hongik.grad.stocks.application.StockQueryService;
import com.hongik.grad.stocks.dto.RecentSearchAddRequest;
import com.hongik.grad.stocks.dto.RecentSearchResponse;
import com.hongik.grad.stocks.dto.StockAutocompleteResponse;
import com.hongik.grad.stocks.dto.StockDailyPriceResponse;
import com.hongik.grad.stocks.dto.StockDetailResponse;
import com.hongik.grad.stocks.dto.StockDisclosuresResponse;
import com.hongik.grad.stocks.dto.StockFinanceResponse;
import com.hongik.grad.stocks.dto.StockNewsResponse;
import com.hongik.grad.stocks.dto.StockRankingResponse;
import com.hongik.grad.stocks.dto.StockSearchResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stocks")
public class StocksController {

  private final StockQueryService stockQueryService;
  private final SearchHistoryService searchHistoryService;

  public StocksController(
      StockQueryService stockQueryService, SearchHistoryService searchHistoryService) {
    this.stockQueryService = stockQueryService;
    this.searchHistoryService = searchHistoryService;
  }

  @GetMapping("/recent")
  public ApiResponse<RecentSearchResponse> recent(@AuthenticationPrincipal AuthUser user) {
    return ApiResponse.success(searchHistoryService.list(user.id()));
  }

  @PostMapping("/recent")
  public ApiResponse<Void> recentAdd(
      @AuthenticationPrincipal AuthUser user, @Valid @RequestBody RecentSearchAddRequest request) {
    searchHistoryService.add(user.id(), request.keyword());
    return ApiResponse.success(null, "최근 검색어가 저장되었습니다");
  }

  @DeleteMapping("/recent")
  public ApiResponse<Void> recentDelete(
      @AuthenticationPrincipal AuthUser user,
      @RequestParam(value = "keyword", required = false) String keyword) {
    int deleted = searchHistoryService.delete(user.id(), keyword);
    return ApiResponse.success(null, deleted + "개의 최근 검색어가 삭제되었습니다");
  }

  @GetMapping("/search")
  public ApiResponse<StockSearchResponse> search(
      @RequestParam("q") String q,
      @RequestParam(value = "page", defaultValue = "1") int page,
      @RequestParam(value = "size", defaultValue = "20") int size) {
    return ApiResponse.success(stockQueryService.search(q, page, size));
  }

  @GetMapping("/autocomplete")
  public ApiResponse<StockAutocompleteResponse> autocomplete(@RequestParam("q") String q) {
    return ApiResponse.success(stockQueryService.autocomplete(q));
  }

  @GetMapping("/ranking")
  public ApiResponse<StockRankingResponse> ranking() {
    return ApiResponse.success(stockQueryService.ranking());
  }

  @GetMapping("/{ticker}")
  public ApiResponse<StockDetailResponse> detail(
      @AuthenticationPrincipal AuthUser user, @PathVariable("ticker") String ticker) {
    return ApiResponse.success(stockQueryService.detail(user == null ? null : user.id(), ticker));
  }

  @GetMapping("/{ticker}/news")
  public ApiResponse<StockNewsResponse> news(@PathVariable("ticker") String ticker) {
    return ApiResponse.success(stockQueryService.news(ticker));
  }

  @GetMapping("/{ticker}/finance")
  public ApiResponse<StockFinanceResponse> finance(@PathVariable("ticker") String ticker) {
    return ApiResponse.success(stockQueryService.finance(ticker));
  }

  @GetMapping("/{ticker}/prices")
  public ApiResponse<StockDailyPriceResponse> prices(
      @PathVariable("ticker") String ticker,
      @RequestParam(value = "days", defaultValue = "30") int days) {
    return ApiResponse.success(stockQueryService.prices(ticker, days));
  }

  @GetMapping("/{ticker}/disclosures")
  public ApiResponse<StockDisclosuresResponse> disclosures(
      @PathVariable("ticker") String ticker,
      @RequestParam(value = "page", defaultValue = "1") int page,
      @RequestParam(value = "size", defaultValue = "20") int size) {
    return ApiResponse.success(stockQueryService.disclosures(ticker, page, size));
  }
}
