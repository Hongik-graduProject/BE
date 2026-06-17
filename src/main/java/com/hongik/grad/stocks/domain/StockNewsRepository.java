package com.hongik.grad.stocks.domain;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockNewsRepository extends JpaRepository<StockNews, Long> {

  List<StockNews> findByStockTickerOrderByPublishedAtDesc(String ticker, Pageable pageable);

  boolean existsByUrl(String url);

  java.util.Optional<StockNews> findTopByStockTickerOrderByCreatedAtDesc(String ticker);
}
