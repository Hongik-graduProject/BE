package com.hongik.grad.stocks.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

  List<SearchHistory> findByUserIdOrderBySearchedAtDesc(UUID userId, Pageable pageable);

  Optional<SearchHistory> findByUserIdAndKeyword(UUID userId, String keyword);

  @Modifying
  @Query("DELETE FROM SearchHistory s WHERE s.userId = :userId AND s.keyword = :keyword")
  int deleteByUserIdAndKeyword(@Param("userId") UUID userId, @Param("keyword") String keyword);

  @Modifying
  @Query("DELETE FROM SearchHistory s WHERE s.userId = :userId")
  int deleteAllByUserId(@Param("userId") UUID userId);
}
