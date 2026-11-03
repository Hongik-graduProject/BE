package com.hongik.grad.stocks.application;

import com.hongik.grad.disclosures.client.DartCorpCodeClient;
import com.hongik.grad.disclosures.client.DartCorpCodeClient.CorpInfo;
import com.hongik.grad.stocks.client.NaverMarketListClient;
import com.hongik.grad.stocks.client.NaverMarketListClient.Listing;
import com.hongik.grad.stocks.domain.Market;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.stocks.domain.StockRepository;
import com.hongik.grad.stocks.event.StocksBootstrappedEvent;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부팅 시 DART corpCode.xml에서 받은 상장 종목 일괄 적재 / 갱신.
 *
 * <p>V3 placeholder seed (K0000*)는 V7 마이그레이션에서 삭제되었고, 그 자리를 이 부트스트랩이 채운다. 이미 같은 ticker가 있으면
 * corp_code와 시장만 갱신한다.
 *
 * <p>DART 목록에는 시장 구분이 없어서 네이버 시장별 목록으로 KOSPI/KOSDAQ 을 정한다. 두 목록 어디에도 없는 종목(코넥스 등)은 새로 넣지 않는다. 이미 있는
 * 종목은 지우지 않는다. 네이버 목록을 못 받으면 새 종목은 다음 부팅까지 넣지 않는다.
 */
@Component
public class CorpCodeBootstrap {

  private static final Logger log = LoggerFactory.getLogger(CorpCodeBootstrap.class);

  private final StockRepository stockRepository;
  private final DartCorpCodeClient corpCodeClient;
  private final NaverMarketListClient marketListClient;
  private final ApplicationEventPublisher eventPublisher;

  public CorpCodeBootstrap(
      StockRepository stockRepository,
      DartCorpCodeClient corpCodeClient,
      NaverMarketListClient marketListClient,
      ApplicationEventPublisher eventPublisher) {
    this.stockRepository = stockRepository;
    this.corpCodeClient = corpCodeClient;
    this.marketListClient = marketListClient;
    this.eventPublisher = eventPublisher;
  }

  @Async("taskExecutor")
  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void bootstrap() {
    try {
      load();
    } finally {
      // 적재가 실패해도 테마 자동 적재(ThemeSenseAutoIngest)는 기존 종목으로 시도할 수 있게 항상 알린다.
      eventPublisher.publishEvent(new StocksBootstrappedEvent());
    }
  }

  private void load() {
    long pending = stockRepository.countByCorpCodeIsNull();
    long total = stockRepository.count();
    log.info(
        "CorpCodeBootstrap: {} total stocks, {} pending corp_code; fetching DART corpCode.xml…",
        total,
        pending);
    Map<String, CorpInfo> map = corpCodeClient.fetchAll();
    if (map.isEmpty()) {
      log.warn("CorpCodeBootstrap: DART returned empty map; will retry on next boot");
      return;
    }
    log.info("CorpCodeBootstrap: DART returned {} listed-ticker entries", map.size());

    Map<String, Listing> markets = marketListClient.fetchAll();
    if (markets.isEmpty()) {
      log.warn("CorpCodeBootstrap: market list unavailable; new stocks skipped until next boot");
    }

    int inserted = 0;
    int updated = 0;
    int outside = 0;
    for (Map.Entry<String, CorpInfo> e : map.entrySet()) {
      String ticker = e.getKey();
      CorpInfo info = e.getValue();
      Listing listing = markets.get(ticker);
      Market market = listing == null ? null : listing.market();
      Stock existing = stockRepository.findById(ticker).orElse(null);
      if (existing == null) {
        if (market == null) {
          outside++;
          continue;
        }
        Stock fresh = new Stock(ticker, safeName(info.corpName(), ticker), market);
        fresh.assignCorpCode(info.corpCode());
        fresh.assignMarketCap(listing.marketCap());
        stockRepository.save(fresh);
        inserted++;
        continue;
      }
      if (existing.getCorpCode() == null || existing.getCorpCode().isBlank()) {
        existing.assignCorpCode(info.corpCode());
        updated++;
      }
      // 목록에 없다고 지우지는 않는다. 지우면 관심종목·보고서까지 CASCADE 로 사라지고,
      // 네이버 목록이 잠깐 덜 온 것만으로도 그렇게 될 수 있다.
      if (market != null) {
        existing.assignMarket(market);
        existing.assignMarketCap(listing.marketCap());
      } else {
        outside++;
      }
    }
    log.info(
        "CorpCodeBootstrap: inserted={} new stocks, updated corp_code for {} existing rows,"
            + " {} outside KOSPI/KOSDAQ lists (not inserted, existing kept)",
        inserted,
        updated,
        outside);
  }

  private static String safeName(String corpName, String fallback) {
    if (corpName == null || corpName.isBlank()) return fallback;
    return corpName.length() > 100 ? corpName.substring(0, 100) : corpName;
  }
}
