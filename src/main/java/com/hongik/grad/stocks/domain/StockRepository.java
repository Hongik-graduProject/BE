package com.hongik.grad.stocks.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StockRepository extends JpaRepository<Stock, String> {

  List<Stock> findByMarket(Market market);

  @Query("SELECT s.ticker FROM Stock s")
  List<String> findAllTickers();
}
