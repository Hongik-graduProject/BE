package com.hongik.grad.stocks.application;

import com.hongik.grad.stocks.client.NaverNewsClient;
import com.hongik.grad.stocks.client.NaverNewsClient.CrawledNews;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.stocks.domain.StockNews;
import com.hongik.grad.stocks.domain.StockNewsRepository;
import com.hongik.grad.stocks.domain.StockRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 종목 상세의 뉴스 탭 호출 시 lazy fetch. 30분 이내 fetch 이력 있으면 skip. */
@Service
public class StockNewsFetchService {

  private static final Logger log = LoggerFactory.getLogger(StockNewsFetchService.class);
  private static final Duration STALE_THRESHOLD = Duration.ofMinutes(30);

  private final NaverNewsClient naverNewsClient;
  private final StockNewsRepository stockNewsRepository;
  private final StockRepository stockRepository;

  public StockNewsFetchService(
      NaverNewsClient naverNewsClient,
      StockNewsRepository stockNewsRepository,
      StockRepository stockRepository) {
    this.naverNewsClient = naverNewsClient;
    this.stockNewsRepository = stockNewsRepository;
    this.stockRepository = stockRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void refreshIfStale(String ticker) {
    Optional<StockNews> latest =
        stockNewsRepository.findTopByStockTickerOrderByCreatedAtDesc(ticker);
    if (latest.isPresent()) {
      // OffsetDateTime created — BaseTimeEntity 없으니 publishedAt 기준으로 stale 판정 우회
      // 새로 fetch한 첫 회는 created_at = now() — 두 번째 호출 이후 skip.
    }
    boolean recentExists =
        latest.isPresent()
            && latest.get().getPublishedAt() != null
            && OffsetDateTime.now()
                .minus(STALE_THRESHOLD)
                .isBefore(extractRefreshAnchor(latest.get()));
    if (recentExists) return;

    Stock stock = stockRepository.findById(ticker).orElse(null);
    if (stock == null) return;

    int newly = 0;
    for (CrawledNews c : naverNewsClient.fetchNews(ticker)) {
      if (c.url() == null || c.url().isBlank()) continue;
      if (stockNewsRepository.existsByUrl(c.url())) continue;
      stockNewsRepository.save(
          new StockNews(stock, c.title(), c.source(), c.url(), c.thumbnailUrl(), c.publishedAt()));
      newly++;
    }
    if (newly > 0) log.info("Crawled {} new news rows for {}", newly, ticker);
  }

  // StockNews는 created_at 외부 노출 없음 → 가장 최근 publishedAt 기준으로 갈음.
  private OffsetDateTime extractRefreshAnchor(StockNews row) {
    return row.getPublishedAt() == null ? OffsetDateTime.MIN : row.getPublishedAt();
  }
}
