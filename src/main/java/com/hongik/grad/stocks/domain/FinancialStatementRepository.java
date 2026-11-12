package com.hongik.grad.stocks.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialStatementRepository extends JpaRepository<FinancialStatement, Long> {

  List<FinancialStatement> findByStockTickerAndPeriodTypeOrderByPeriodDesc(
      String ticker, PeriodType periodType);

  List<FinancialStatement> findByStockTickerOrderByPeriodDesc(String ticker);

  Optional<FinancialStatement> findByStockTickerAndPeriodTypeAndPeriod(
      String ticker, PeriodType periodType, String period);

  Optional<FinancialStatement> findByStockTickerAndPeriodTypeAndPeriodAndSjDiv(
      String ticker, PeriodType periodType, String period, String sjDiv);

  /** disclosed_at 이 NULL 인 행은 SQL 비교에서 빠진다. */
  List<FinancialStatement> findByStockTickerAndDisclosedAtLessThanEqualOrderByPeriodDesc(
      String ticker, LocalDate asOf);

  long countByStockTickerAndDisclosedAtIsNotNull(String ticker);
}
