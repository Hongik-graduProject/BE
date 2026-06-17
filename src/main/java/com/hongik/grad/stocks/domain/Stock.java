package com.hongik.grad.stocks.domain;

import com.hongik.grad.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "stocks")
public class Stock extends BaseTimeEntity {

  @Id
  @Column(length = 10)
  private String ticker;

  @Column(nullable = false, length = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Market market;

  @Column(length = 50)
  private String category;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "listed_at")
  private LocalDate listedAt;

  @Column(name = "corp_code", length = 8)
  private String corpCode;

  protected Stock() {}

  public Stock(String ticker, String name, Market market) {
    this.ticker = ticker;
    this.name = name;
    this.market = market;
  }

  public String getTicker() {
    return ticker;
  }

  public String getName() {
    return name;
  }

  public Market getMarket() {
    return market;
  }

  public String getCategory() {
    return category;
  }

  public String getDescription() {
    return description;
  }

  public LocalDate getListedAt() {
    return listedAt;
  }

  public String getCorpCode() {
    return corpCode;
  }

  public void assignCorpCode(String corpCode) {
    this.corpCode = corpCode;
  }
}
