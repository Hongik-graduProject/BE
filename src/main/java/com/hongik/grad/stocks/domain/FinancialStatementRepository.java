package com.hongik.grad.stocks.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialStatementRepository extends JpaRepository<FinancialStatement, Long> {

  List<FinancialStatement> findByStockTickerAndPeriodTypeOrderByPeriodDesc(
      String ticker, PeriodType periodType);

  List<FinancialStatement> findByStockTickerOrderByPeriodDesc(String ticker);
}
