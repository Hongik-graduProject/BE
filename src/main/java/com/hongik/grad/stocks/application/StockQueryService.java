package com.hongik.grad.stocks.application;

import com.hongik.grad.analysis.dto.PaginationMeta;
import com.hongik.grad.common.api.ErrorCode;
import com.hongik.grad.common.exception.DomainException;
import com.hongik.grad.disclosures.domain.Disclosure;
import com.hongik.grad.disclosures.domain.DisclosureRepository;
import com.hongik.grad.folders.domain.FavoriteRepository;
import com.hongik.grad.stocks.domain.FinancialStatement;
import com.hongik.grad.stocks.domain.FinancialStatementRepository;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.stocks.domain.StockNews;
import com.hongik.grad.stocks.domain.StockNewsRepository;
import com.hongik.grad.stocks.domain.StockRepository;
import com.hongik.grad.stocks.dto.FinanceItem;
import com.hongik.grad.stocks.dto.KrxQuote;
import com.hongik.grad.stocks.dto.StockAutocompleteResponse;
import com.hongik.grad.stocks.dto.StockBriefItem;
import com.hongik.grad.stocks.dto.StockDetailResponse;
import com.hongik.grad.stocks.dto.StockDisclosureItem;
import com.hongik.grad.stocks.dto.StockDisclosuresResponse;
import com.hongik.grad.stocks.dto.StockFinanceResponse;
import com.hongik.grad.stocks.dto.StockNewsItem;
import com.hongik.grad.stocks.dto.StockNewsResponse;
import com.hongik.grad.stocks.dto.StockRankingResponse;
import com.hongik.grad.stocks.dto.StockSearchResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stocks 도메인 조회 서비스 — 검색 / 자동완성 / 인기 / 상세. */
@Service
@Transactional(readOnly = true)
public class StockQueryService {

  private static final int AUTOCOMPLETE_LIMIT = 10;
  private static final int RANKING_LIMIT = 20;

  private final StockRepository stockRepository;
  private final FavoriteRepository favoriteRepository;
  private final KrxQuoteService krxQuoteService;
  private final StockNewsRepository stockNewsRepository;
  private final FinancialStatementRepository financialStatementRepository;
  private final DisclosureRepository disclosureRepository;
  private final StockNewsFetchService stockNewsFetchService;
  private final FinancialStatementFetchService financialStatementFetchService;

  public StockQueryService(
      StockRepository stockRepository,
      FavoriteRepository favoriteRepository,
      KrxQuoteService krxQuoteService,
      StockNewsRepository stockNewsRepository,
      FinancialStatementRepository financialStatementRepository,
      DisclosureRepository disclosureRepository,
      StockNewsFetchService stockNewsFetchService,
      FinancialStatementFetchService financialStatementFetchService) {
    this.stockRepository = stockRepository;
    this.favoriteRepository = favoriteRepository;
    this.krxQuoteService = krxQuoteService;
    this.stockNewsRepository = stockNewsRepository;
    this.financialStatementRepository = financialStatementRepository;
    this.disclosureRepository = disclosureRepository;
    this.stockNewsFetchService = stockNewsFetchService;
    this.financialStatementFetchService = financialStatementFetchService;
  }

  /** §4-8 GET /stocks/{ticker}/news — 네이버 금융 lazy 크롤링 후 최신 20건. */
  public StockNewsResponse news(String ticker) {
    if (!stockRepository.existsById(ticker)) {
      throw new DomainException(ErrorCode.NOT_FOUND, "종목을 찾을 수 없습니다");
    }
    // 30분 이내 크롤링 이력 없으면 갱신.
    try {
      stockNewsFetchService.refreshIfStale(ticker);
    } catch (Exception ignored) {
      // 크롤링 실패해도 DB 캐시된 뉴스는 반환
    }
    List<StockNews> rows =
        stockNewsRepository.findByStockTickerOrderByPublishedAtDesc(
            ticker, PageRequest.of(0, 20));
    return new StockNewsResponse(
        rows.stream()
            .map(
                n ->
                    new StockNewsItem(
                        n.getId(),
                        n.getTitle(),
                        n.getSource(),
                        n.getUrl(),
                        n.getThumbnailUrl(),
                        n.getPublishedAt()))
            .toList());
  }

  /** §4-9 GET /stocks/{ticker}/finance — 분기·연간 재무. 비어있으면 DART에서 lazy fetch. */
  public StockFinanceResponse finance(String ticker) {
    if (!stockRepository.existsById(ticker)) {
      throw new DomainException(ErrorCode.NOT_FOUND, "종목을 찾을 수 없습니다");
    }
    try {
      financialStatementFetchService.refreshIfMissing(ticker);
    } catch (Exception ignored) {
      // 실패해도 DB 캐시된 재무 데이터는 반환
    }
    List<FinancialStatement> rows =
        financialStatementRepository.findByStockTickerOrderByPeriodDesc(ticker);
    return new StockFinanceResponse(
        rows.stream()
            .map(
                f ->
                    new FinanceItem(
                        f.getPeriodType().name(),
                        f.getPeriod(),
                        f.getRevenue(),
                        f.getCostOfRevenue(),
                        f.getGrossProfit(),
                        f.getOperatingProfit(),
                        f.getNetIncome(),
                        f.getTotalAssets(),
                        f.getTotalLiabilities(),
                        f.getTotalEquity(),
                        f.getPer(),
                        f.getPbr(),
                        f.getEps(),
                        f.getDividendYield()))
            .toList());
  }

  /** §4-10 GET /stocks/{ticker}/disclosures — DART 폴러 적재본. */
  public StockDisclosuresResponse disclosures(String ticker, int page, int size) {
    if (!stockRepository.existsById(ticker)) {
      throw new DomainException(ErrorCode.NOT_FOUND, "종목을 찾을 수 없습니다");
    }
    PageRequest pageable = PageRequest.of(Math.max(0, page - 1), Math.max(1, size));
    org.springframework.data.domain.Page<Disclosure> result =
        disclosureRepository.findByStockTickerOrderByFiledAtDesc(ticker, pageable);
    List<StockDisclosureItem> items =
        result.stream()
            .map(
                d ->
                    new StockDisclosureItem(
                        d.getId(),
                        d.getTitle(),
                        d.getType().name(),
                        d.getFiledAt(),
                        d.getDartUrl() != null && !d.getDartUrl().isBlank()
                            ? d.getDartUrl()
                            : "https://dart.fss.or.kr/dsaf001/main.do?rcpNo="
                                + d.getDartFilingId()))
            .toList();
    return new StockDisclosuresResponse(
        items, PaginationMeta.of(page, size, result.getTotalElements()));
  }

  public StockSearchResponse search(String q, int page, int size) {
    if (q == null || q.isBlank()) {
      throw new DomainException(ErrorCode.INVALID_PARAM, "검색어를 입력해 주세요");
    }
    Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.max(1, size));
    Page<Stock> result = stockRepository.search(q.trim(), pageable);
    Map<String, KrxQuote> quotes =
        krxQuoteService.getQuotes(result.stream().map(Stock::getTicker).toList()).stream()
            .collect(Collectors.toMap(KrxQuote::ticker, Function.identity(), (a, b) -> a));
    List<StockBriefItem> items = result.stream().map(s -> toBrief(s, quotes.get(s.getTicker()))).toList();
    PaginationMeta meta = PaginationMeta.of(page, size, result.getTotalElements());
    return new StockSearchResponse(items, meta);
  }

  public StockAutocompleteResponse autocomplete(String q) {
    if (q == null || q.isBlank()) {
      return new StockAutocompleteResponse(List.of());
    }
    List<Stock> rows =
        stockRepository.autocomplete(q.trim(), PageRequest.of(0, AUTOCOMPLETE_LIMIT));
    return new StockAutocompleteResponse(rows.stream().map(s -> toBrief(s, null)).toList());
  }

  /** v1: stock_view_counts 미구현 — 종목 마스터에서 임의 상위 20개. v2에서 실 view_count 정렬. */
  public StockRankingResponse ranking() {
    List<Stock> rows = stockRepository.findAll(PageRequest.of(0, RANKING_LIMIT)).getContent();
    Map<String, KrxQuote> quotes =
        krxQuoteService.getQuotes(rows.stream().map(Stock::getTicker).toList()).stream()
            .collect(Collectors.toMap(KrxQuote::ticker, Function.identity(), (a, b) -> a));
    return new StockRankingResponse(
        rows.stream().map(s -> toBrief(s, quotes.get(s.getTicker()))).toList());
  }

  public StockDetailResponse detail(UUID userId, String ticker) {
    Stock stock =
        stockRepository
            .findById(ticker)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "종목을 찾을 수 없습니다"));
    KrxQuote quote = krxQuoteService.getQuote(ticker);
    boolean isFavorite =
        userId != null && favoriteRepository.existsByUserIdAndTicker(userId, ticker);
    return new StockDetailResponse(
        stock.getTicker(),
        stock.getName(),
        stock.getMarket().name(),
        stock.getCategory(),
        stock.getDescription(),
        stock.getListedAt(),
        quote == null ? null : quote.currentPrice(),
        quote == null ? null : quote.changeRate(),
        quote == null ? null : quote.changeAmount(),
        isFavorite);
  }

  private StockBriefItem toBrief(Stock s, KrxQuote q) {
    return new StockBriefItem(
        s.getTicker(),
        s.getName(),
        s.getMarket().name(),
        s.getCategory(),
        q == null ? null : q.currentPrice(),
        q == null ? null : q.changeRate(),
        q == null ? null : q.changeAmount());
  }
}
