package com.hongik.grad.themes.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ThemeRepository extends JpaRepository<Theme, Long> {

  Optional<Theme> findByName(String name);

  /** 테마별 1순위(rank = 1) 종목 수. 종목이 없는 테마도 0으로 포함한다. */
  @Query(
      "SELECT t.id AS id, t.name AS name, t.description AS description, COUNT(st.id) AS stockCount"
          + " FROM Theme t LEFT JOIN StockTheme st ON st.theme = t AND st.rank = 1"
          + " GROUP BY t.id, t.name, t.description"
          + " ORDER BY COUNT(st.id) DESC, t.id")
  List<ThemeStockCount> findAllWithPrimaryStockCount();

  interface ThemeStockCount {
    Long getId();

    String getName();

    String getDescription();

    long getStockCount();
  }
}
