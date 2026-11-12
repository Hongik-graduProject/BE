package com.hongik.grad.timemachine.domain;

import com.hongik.grad.analysis.domain.AnalysisReport;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** 판정에 필요한 공시(유형·filed_at)를 같이 읽는다. 보고서마다 공시를 따로 부르지 않으려고 fetch join 한다. */
public interface ReplayReportRepository extends Repository<AnalysisReport, Long> {

  @Query("SELECT r FROM AnalysisReport r LEFT JOIN FETCH r.disclosure WHERE r.id = :id")
  Optional<AnalysisReport> findWithDisclosure(Long id);

  @Query(
      "SELECT r FROM AnalysisReport r JOIN FETCH r.disclosure d"
          + " WHERE r.stock.ticker = :ticker ORDER BY d.filedAt")
  List<AnalysisReport> findByTickerWithDisclosure(String ticker);
}
