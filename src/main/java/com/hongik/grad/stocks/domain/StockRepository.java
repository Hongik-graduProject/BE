package com.hongik.grad.stocks.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, String> {

  List<Stock> findByMarket(Market market);
}
