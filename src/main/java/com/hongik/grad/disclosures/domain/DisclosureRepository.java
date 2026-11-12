package com.hongik.grad.disclosures.domain;

import com.hongik.grad.stocks.domain.Market;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DisclosureRepository extends JpaRepository<Disclosure, Long> {

  boolean existsByDartFilingId(String dartFilingId);

  @Query("SELECT d.dartFilingId FROM Disclosure d WHERE d.dartFilingId IN :ids")
  List<String> findDartFilingIdByDartFilingIdIn(Collection<String> ids);

  Optional<Disclosure> findByDartFilingId(String dartFilingId);

  Optional<Disclosure> findTopByOrderByFiledAtDesc();

  Page<Disclosure> findByStockTickerOrderByFiledAtDesc(String ticker, Pageable pageable);

  long countByFiledAtGreaterThanEqualAndFiledAtLessThan(OffsetDateTime from, OffsetDateTime to);

  /** Radar window [from, to), newest first. {@code stock} is fetched for the point's name. */
  @EntityGraph(attributePaths = {"stock"})
  List<Disclosure> findByFiledAtGreaterThanEqualAndFiledAtLessThanOrderByFiledAtDesc(
      OffsetDateTime from, OffsetDateTime to);

  /** 기간 내 공시 건수 상위 종목. 동률은 ticker 순. 개수는 호출자의 {@link Pageable}. */
  @Query(
      "SELECT s.ticker AS ticker, s.name AS name, s.market AS market, COUNT(d) AS disclosureCount"
          + " FROM Disclosure d JOIN d.stock s"
          + " WHERE d.filedAt >= :from"
          + " GROUP BY s.ticker, s.name, s.market"
          + " ORDER BY COUNT(d) DESC, s.ticker")
  List<TickerDisclosureCount> findTopTickersByDisclosureCountSince(
      OffsetDateTime from, Pageable pageable);

  interface TickerDisclosureCount {
    String getTicker();

    String getName();

    Market getMarket();

    long getDisclosureCount();
  }
}
