package com.hongik.grad.stocks.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "search_history",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_search_user_keyword",
            columnNames = {"user_id", "keyword"}))
public class SearchHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
  private UUID userId;

  @Column(nullable = false, length = 100)
  private String keyword;

  @Column(name = "searched_at", nullable = false)
  private OffsetDateTime searchedAt;

  protected SearchHistory() {}

  public SearchHistory(UUID userId, String keyword) {
    this.userId = userId;
    this.keyword = keyword;
    this.searchedAt = OffsetDateTime.now();
  }

  public void touch() {
    this.searchedAt = OffsetDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getKeyword() {
    return keyword;
  }

  public OffsetDateTime getSearchedAt() {
    return searchedAt;
  }
}
