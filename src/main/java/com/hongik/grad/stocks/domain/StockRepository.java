package com.hongik.grad.stocks.domain;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockRepository extends JpaRepository<Stock, String> {

  List<Stock> findByMarket(Market market);

  @Query("SELECT s.ticker FROM Stock s")
  List<String> findAllTickers();

  long countByCorpCodeIsNull();

  @Query("SELECT s.ticker FROM Stock s WHERE s.corpCode IS NULL")
  List<String> findTickersWithoutCorpCode();

  /** §4-1 통합 검색: name·ticker 부분 일치 (case-insensitive). */
  @Query(
      "SELECT s FROM Stock s "
          + "WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%')) "
          + "   OR LOWER(s.ticker) LIKE LOWER(CONCAT(:q, '%'))")
  Page<Stock> search(@Param("q") String q, Pageable pageable);

  /** §4-2 자동완성: prefix 매칭 위주, 짧은 결과만. */
  @Query(
      "SELECT s FROM Stock s "
          + "WHERE LOWER(s.name) LIKE LOWER(CONCAT(:q, '%')) "
          + "   OR LOWER(s.ticker) LIKE LOWER(CONCAT(:q, '%')) "
          + "ORDER BY s.ticker ASC")
  List<Stock> autocomplete(@Param("q") String q, Pageable pageable);
}
