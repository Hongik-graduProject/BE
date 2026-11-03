package com.hongik.grad.themes.application;

import com.hongik.grad.analysis.dto.PaginationMeta;
import com.hongik.grad.common.api.ErrorCode;
import com.hongik.grad.common.exception.DomainException;
import com.hongik.grad.stocks.application.KrxQuoteService;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.stocks.dto.KrxQuote;
import com.hongik.grad.themes.domain.StockTheme;
import com.hongik.grad.themes.domain.StockThemeRepository;
import com.hongik.grad.themes.domain.Theme;
import com.hongik.grad.themes.domain.ThemeRepository;
import com.hongik.grad.themes.dto.ThemeListResponse;
import com.hongik.grad.themes.dto.ThemeStockItem;
import com.hongik.grad.themes.dto.ThemeStocksResponse;
import com.hongik.grad.themes.dto.ThemeSummaryResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 테마 탐색 — 테마 목록과 테마별(1순위 테마 기준) 종목 목록. */
@Service
public class ThemeExploreService {

  private static final short PRIMARY_RANK = 1;
  private static final int MAX_PAGE_SIZE = 50;
  // 데모 테마의 일치도(probability)는 규칙으로 만든 값이라 순서로 쓰면 대표 종목이 묻힌다. 정렬은 저장소 쿼리의 시가총액 순이다.

  private final ThemeRepository themeRepository;
  private final StockThemeRepository stockThemeRepository;
  private final KrxQuoteService krxQuoteService;

  public ThemeExploreService(
      ThemeRepository themeRepository,
      StockThemeRepository stockThemeRepository,
      KrxQuoteService krxQuoteService) {
    this.themeRepository = themeRepository;
    this.stockThemeRepository = stockThemeRepository;
    this.krxQuoteService = krxQuoteService;
  }

  @Transactional(readOnly = true)
  public ThemeListResponse themes() {
    return new ThemeListResponse(
        themeRepository.findAllWithPrimaryStockCount().stream()
            .map(
                r ->
                    new ThemeSummaryResponse(
                        r.getId(), r.getName(), r.getDescription(), r.getStockCount()))
            .toList());
  }

  // 시세 일괄 조회(최대 수 초) 동안 DB 연결을 잡고 있지 않는다. stock 은 EntityGraph 로 함께 읽어 온다.
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public ThemeStocksResponse stocks(long themeId, int page, int size) {
    Theme theme =
        themeRepository
            .findById(themeId)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "테마를 찾을 수 없습니다"));
    int pageNo = Math.max(1, page);
    int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
    Page<StockTheme> result =
        stockThemeRepository.findByThemeOrderByMarketCap(
            themeId, PRIMARY_RANK, PageRequest.of(pageNo - 1, pageSize));
    Map<String, KrxQuote> quotes =
        krxQuoteService
            .getQuotes(result.stream().map(st -> st.getStock().getTicker()).toList())
            .stream()
            .collect(Collectors.toMap(KrxQuote::ticker, Function.identity(), (a, b) -> a));
    List<ThemeStockItem> items =
        result.stream().map(st -> toItem(st, quotes.get(st.getStock().getTicker()))).toList();
    return new ThemeStocksResponse(
        new ThemeSummaryResponse(
            theme.getId(), theme.getName(), theme.getDescription(), result.getTotalElements()),
        items,
        PaginationMeta.of(pageNo, pageSize, result.getTotalElements()));
  }

  private static ThemeStockItem toItem(StockTheme st, KrxQuote q) {
    Stock s = st.getStock();
    // 시세 조회 실패는 KrxQuote.empty(가격 0)로 오므로 null 로 내려 "가격 없음"을 구분한다.
    boolean noQuote = q == null || q.currentPrice() == 0;
    return new ThemeStockItem(
        s.getTicker(),
        s.getName(),
        s.getMarket().name(),
        noQuote ? null : q.currentPrice(),
        noQuote ? null : q.changeRate(),
        noQuote ? null : q.changeAmount(),
        st.getProbability());
  }
}
