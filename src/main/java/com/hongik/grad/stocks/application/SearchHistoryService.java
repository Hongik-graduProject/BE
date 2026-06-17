package com.hongik.grad.stocks.application;

import com.hongik.grad.stocks.domain.SearchHistory;
import com.hongik.grad.stocks.domain.SearchHistoryRepository;
import com.hongik.grad.stocks.dto.RecentSearchItem;
import com.hongik.grad.stocks.dto.RecentSearchResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** §4-3 ~ §4-5 최근 검색어. */
@Service
public class SearchHistoryService {

  private static final int RECENT_LIMIT = 10;

  private final SearchHistoryRepository repository;

  public SearchHistoryService(SearchHistoryRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public RecentSearchResponse list(UUID userId) {
    List<SearchHistory> rows =
        repository.findByUserIdOrderBySearchedAtDesc(userId, PageRequest.of(0, RECENT_LIMIT));
    List<RecentSearchItem> items =
        rows.stream()
            .map(s -> new RecentSearchItem(s.getId(), s.getKeyword(), s.getSearchedAt()))
            .toList();
    return new RecentSearchResponse(items);
  }

  /** UPSERT — 동일 키워드 재검색 시 searched_at만 갱신. */
  @Transactional
  public void add(UUID userId, String keyword) {
    String normalized = keyword.trim();
    if (normalized.isEmpty()) return;
    repository
        .findByUserIdAndKeyword(userId, normalized)
        .ifPresentOrElse(
            SearchHistory::touch,
            () -> repository.save(new SearchHistory(userId, normalized)));
  }

  /** keyword 지정 시 해당 항목만, null이면 전체 삭제. */
  @Transactional
  public int delete(UUID userId, String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return repository.deleteAllByUserId(userId);
    }
    return repository.deleteByUserIdAndKeyword(userId, keyword.trim());
  }
}
