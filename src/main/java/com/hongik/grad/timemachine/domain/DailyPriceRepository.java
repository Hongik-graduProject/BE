package com.hongik.grad.timemachine.domain;

import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * daily_prices 접근. JPA 엔티티 대신 JdbcTemplate 을 쓴다: 복합키 엔티티 없이 {@code ON CONFLICT DO NOTHING} 배치 삽입과
 * 날짜 구간 조회만 있으면 된다.
 */
@Repository
public class DailyPriceRepository {

  private final JdbcTemplate jdbcTemplate;

  public DailyPriceRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** 저장된 시세의 첫날·마지막날. 한 행도 없으면 null. */
  public DateRange range(String ticker) {
    return jdbcTemplate.queryForObject(
        "SELECT MIN(trade_date), MAX(trade_date) FROM daily_prices WHERE ticker = ?",
        (rs, i) -> {
          LocalDate min = rs.getObject(1, LocalDate.class);
          return min == null ? null : new DateRange(min, rs.getObject(2, LocalDate.class));
        },
        ticker);
  }

  /** {@code from} 이후 시세, 날짜 오름차순. */
  public List<PricePoint> findFrom(String ticker, LocalDate from) {
    return jdbcTemplate.query(
        "SELECT trade_date, close FROM daily_prices"
            + " WHERE ticker = ? AND trade_date >= ? ORDER BY trade_date",
        (rs, i) -> new PricePoint(rs.getObject(1, LocalDate.class), rs.getLong(2)),
        ticker,
        from);
  }

  /** 이미 있는 (ticker, trade_date) 는 건드리지 않는다. 동시에 두 요청이 받아도 안전하다. */
  public void insertIgnore(String ticker, List<PricePoint> rows) {
    jdbcTemplate.batchUpdate(
        "INSERT INTO daily_prices (ticker, trade_date, close) VALUES (?, ?, ?)"
            + " ON CONFLICT DO NOTHING",
        rows,
        500,
        (PreparedStatement ps, PricePoint p) -> {
          ps.setString(1, ticker);
          ps.setObject(2, p.date());
          ps.setLong(3, p.close());
        });
  }

  public record DateRange(LocalDate min, LocalDate max) {}
}
