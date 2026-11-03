package com.hongik.grad.themes.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.hongik.grad.disclosures.domain.Disclosure;
import com.hongik.grad.disclosures.domain.DisclosureRepository;
import com.hongik.grad.disclosures.domain.DisclosureRepository.TickerDisclosureCount;
import com.hongik.grad.disclosures.domain.DisclosureType;
import com.hongik.grad.stocks.domain.Market;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.support.TestcontainersConfig;
import com.hongik.grad.themes.domain.ThemeRepository.ThemeStockCount;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

/** 테마 탐색·시장 요약에 쓰는 집계 쿼리. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test-db")
@Import(TestcontainersConfig.class)
class ThemeExploreRepositoryTest {

  @Autowired private ThemeRepository themeRepository;
  @Autowired private StockThemeRepository stockThemeRepository;
  @Autowired private DisclosureRepository disclosureRepository;
  @Autowired private TestEntityManager em;

  @Test
  void 테마별_1순위_종목수와_종목_페이지() {
    Theme ai = theme("T-AI");
    Theme bio = theme("T-BIO");
    Theme empty = theme("T-EMPTY");
    Stock s1 = new Stock("900001", "가", Market.KOSPI);
    s1.assignMarketCap(100L);
    em.persist(s1);
    Stock s2 = new Stock("900002", "나", Market.KOSDAQ);
    s2.assignMarketCap(500L);
    em.persist(s2);
    Stock s3 = em.persist(new Stock("900003", "다", Market.KOSPI)); // 시가총액 없음
    stockTheme(s1, ai, (short) 1, 0.5);
    stockTheme(s2, ai, (short) 1, 0.9);
    stockTheme(s3, ai, (short) 1, 0.5);
    stockTheme(s1, bio, (short) 2, 0.3); // 2순위는 세지 않는다
    em.flush();
    em.clear();

    List<ThemeStockCount> counts =
        themeRepository.findAllWithPrimaryStockCount().stream()
            .filter(r -> r.getName().startsWith("T-"))
            .toList();
    assertThat(counts)
        .extracting(ThemeStockCount::getName)
        .containsExactly("T-AI", "T-BIO", "T-EMPTY");
    assertThat(counts).extracting(ThemeStockCount::getStockCount).containsExactly(3L, 0L, 0L);
    assertThat(empty.getId()).isNotNull();

    Page<StockTheme> page =
        stockThemeRepository.findByThemeIdAndRank(
            ai.getId(),
            (short) 1,
            PageRequest.of(
                0, 2, Sort.by(Sort.Order.desc("probability"), Sort.Order.asc("stock.ticker"))));
    assertThat(page.getTotalElements()).isEqualTo(3);
    assertThat(page.getContent())
        .extracting(st -> st.getStock().getTicker())
        .containsExactly("900002", "900001");

    // 서비스가 쓰는 정렬: 시가총액 내림차순, 값이 없으면 맨 뒤.
    Page<StockTheme> byCap =
        stockThemeRepository.findByThemeOrderByMarketCap(
            ai.getId(), (short) 1, PageRequest.of(0, 3));
    assertThat(byCap.getTotalElements()).isEqualTo(3);
    assertThat(byCap.getContent())
        .extracting(st -> st.getStock().getTicker())
        .containsExactly("900002", "900001", "900003");
  }

  @Test
  void 기간_공시_건수와_상위_종목() {
    Stock s1 = em.persist(new Stock("900001", "가", Market.KOSPI));
    Stock s2 = em.persist(new Stock("900002", "나", Market.KOSDAQ));
    Stock s3 = em.persist(new Stock("900003", "다", Market.KOSPI));
    // KST 11-02 00:30 (UTC 11-01) 와 KST 11-02 23:59 는 같은 날, KST 11-01 23:59 는 전날.
    filing(s1, "F1", "2026-11-02T00:30+09:00");
    filing(s1, "F2", "2026-11-02T23:59+09:00");
    filing(s2, "F3", "2026-11-02T10:00+09:00");
    filing(s3, "F4", "2026-11-02T11:00+09:00");
    filing(s3, "F5", "2026-11-01T23:59+09:00");
    filing(s2, "F6", "2026-10-01T10:00+09:00"); // 기간 밖
    em.flush();
    em.clear();

    assertThat(
            disclosureRepository.countByFiledAtGreaterThanEqualAndFiledAtLessThan(
                OffsetDateTime.parse("2026-11-02T00:00+09:00"),
                OffsetDateTime.parse("2026-11-03T00:00+09:00")))
        .isEqualTo(4);

    List<TickerDisclosureCount> top =
        disclosureRepository.findTopTickersByDisclosureCountSince(
            OffsetDateTime.parse("2026-10-28T00:00+09:00"), PageRequest.of(0, 2));
    // s1, s3 가 2건 동률 → ticker 순, s2 는 기간 안 1건이라 잘린다.
    assertThat(top)
        .extracting(TickerDisclosureCount::getTicker)
        .containsExactly("900001", "900003");
    assertThat(top).extracting(TickerDisclosureCount::getDisclosureCount).containsExactly(2L, 2L);
    assertThat(top.get(0).getMarket()).isEqualTo(Market.KOSPI);
    assertThat(top.get(0).getName()).isEqualTo("가");
  }

  private Theme theme(String name) {
    Theme t = new Theme(name);
    t.setSource("test");
    return em.persist(t);
  }

  private void stockTheme(Stock stock, Theme theme, short rank, double probability) {
    StockTheme st = new StockTheme(stock, theme, rank);
    st.setProbability(probability);
    st.setPrimary(rank == 1);
    st.setModelVersion("test");
    st.setPredictedAt(OffsetDateTime.parse("2026-11-01T00:00Z"));
    em.persist(st);
  }

  private void filing(Stock stock, String id, String filedAt) {
    em.persist(
        new Disclosure(stock, id, "공시", DisclosureType.EVENT, OffsetDateTime.parse(filedAt)));
  }
}
