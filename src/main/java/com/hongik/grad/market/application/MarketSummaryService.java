package com.hongik.grad.market.application;

import com.hongik.grad.disclosures.domain.Disclosure;
import com.hongik.grad.disclosures.domain.DisclosureRepository;
import com.hongik.grad.market.client.NaverIndexClient;
import com.hongik.grad.market.dto.HotStockItem;
import com.hongik.grad.market.dto.MarketIndexResponse;
import com.hongik.grad.market.dto.MarketSummaryResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** 홈 시장 요약 — 지수(네이버) + 공시 집계(DB). 트랜잭션 없이 쿼리마다 짧게 연결을 쓴다. */
@Service
public class MarketSummaryService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final int HOT_STOCK_DAYS = 7;
  private static final int HOT_STOCK_LIMIT = 5;

  private final NaverIndexClient naverIndexClient;
  private final DisclosureRepository disclosureRepository;
  private final Clock clock;

  public MarketSummaryService(
      NaverIndexClient naverIndexClient, DisclosureRepository disclosureRepository, Clock clock) {
    this.naverIndexClient = naverIndexClient;
    this.disclosureRepository = disclosureRepository;
    this.clock = clock;
  }

  public MarketSummaryResponse summary() {
    List<MarketIndexResponse> indices =
        Stream.of(naverIndexClient.fetch("KOSPI", "코스피"), naverIndexClient.fetch("KOSDAQ", "코스닥"))
            .flatMap(Optional::stream)
            .toList();

    LocalDate latestDate =
        disclosureRepository
            .findTopByOrderByFiledAtDesc()
            .map(Disclosure::getFiledAt)
            .map(t -> t.atZoneSameInstant(KST).toLocalDate())
            .orElse(null);
    Long latestCount =
        latestDate == null
            ? null
            : disclosureRepository.countByFiledAtGreaterThanEqualAndFiledAtLessThan(
                latestDate.atStartOfDay(KST).toOffsetDateTime(),
                latestDate.plusDays(1).atStartOfDay(KST).toOffsetDateTime());

    LocalDate today = LocalDate.now(clock.withZone(KST));
    List<HotStockItem> hotStocks =
        disclosureRepository
            .findTopTickersByDisclosureCountSince(
                today.minusDays(HOT_STOCK_DAYS - 1).atStartOfDay(KST).toOffsetDateTime(),
                PageRequest.of(0, HOT_STOCK_LIMIT))
            .stream()
            .map(
                r ->
                    new HotStockItem(
                        r.getTicker(), r.getName(), r.getMarket().name(), r.getDisclosureCount()))
            .toList();

    return new MarketSummaryResponse(indices, latestDate, latestCount, hotStocks);
  }
}
