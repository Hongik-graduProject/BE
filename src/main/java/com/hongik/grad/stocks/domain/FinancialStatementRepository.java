package com.hongik.grad.stocks.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialStatementRepository extends JpaRepository<FinancialStatement, Long> {

  List<FinancialStatement> findByStockTickerAndPeriodTypeOrderByPeriodDesc(
      String ticker, PeriodType periodType);

  List<FinancialStatement> findByStockTickerOrderByPeriodDesc(String ticker);

  Optional<FinancialStatement> findByStockTickerAndPeriodTypeAndPeriod(
      String ticker, PeriodType periodType, String period);
}
