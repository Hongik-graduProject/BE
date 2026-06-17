package com.hongik.grad.stocks.application;

import com.hongik.grad.disclosures.client.DartCorpCodeClient;
import com.hongik.grad.disclosures.client.DartCorpCodeClient.CorpInfo;
import com.hongik.grad.stocks.domain.Market;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.stocks.domain.StockRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부팅 시 DART corpCode.xml에서 받은 상장 종목 일괄 적재 / 갱신.
 *
 * <p>V3 placeholder seed (K0000*)는 V7 마이그레이션에서 삭제되었고, 그 자리를 이 부트스트랩이
 * 채운다. 이미 같은 ticker가 있으면 corp_code만 갱신하고 다른 필드는 건드리지 않는다.
 */
@Component
public class CorpCodeBootstrap {

  private static final Logger log = LoggerFactory.getLogger(CorpCodeBootstrap.class);

  private final StockRepository stockRepository;
  private final DartCorpCodeClient corpCodeClient;

  public CorpCodeBootstrap(
      StockRepository stockRepository, DartCorpCodeClient corpCodeClient) {
    this.stockRepository = stockRepository;
    this.corpCodeClient = corpCodeClient;
  }

  @Async("taskExecutor")
  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void bootstrap() {
    long pending = stockRepository.countByCorpCodeIsNull();
    long total = stockRepository.count();
    log.info(
        "CorpCodeBootstrap: {} total stocks, {} pending corp_code; fetching DART corpCode.xml…",
        total, pending);
    Map<String, CorpInfo> map = corpCodeClient.fetchAll();
    if (map.isEmpty()) {
      log.warn("CorpCodeBootstrap: DART returned empty map; will retry on next boot");
      return;
    }
    log.info("CorpCodeBootstrap: DART returned {} listed-ticker entries", map.size());

    int inserted = 0;
    int updated = 0;
    for (Map.Entry<String, CorpInfo> e : map.entrySet()) {
      String ticker = e.getKey();
      CorpInfo info = e.getValue();
      Stock existing = stockRepository.findById(ticker).orElse(null);
      if (existing == null) {
        Stock fresh = new Stock(ticker, safeName(info.corpName(), ticker), Market.KOSPI);
        fresh.assignCorpCode(info.corpCode());
        stockRepository.save(fresh);
        inserted++;
      } else if (existing.getCorpCode() == null || existing.getCorpCode().isBlank()) {
        existing.assignCorpCode(info.corpCode());
        updated++;
      }
    }
    log.info(
        "CorpCodeBootstrap: inserted={} new stocks, updated corp_code for {} existing rows",
        inserted, updated);
  }

  private static String safeName(String corpName, String fallback) {
    if (corpName == null || corpName.isBlank()) return fallback;
    return corpName.length() > 100 ? corpName.substring(0, 100) : corpName;
  }
}
