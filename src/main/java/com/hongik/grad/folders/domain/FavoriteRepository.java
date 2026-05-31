package com.hongik.grad.folders.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

  List<Favorite> findByFolderIdOrderByAddedAtDesc(Long folderId);

  /** All favorites owned by a user across folders — batch source for §3-1 unread aggregation. */
  List<Favorite> findByFolderUserId(UUID userId);

  /** Distinct user ids that favorited a ticker — fan-out target for notifications (PR-09). */
  @Query("SELECT DISTINCT f.folder.userId FROM Favorite f WHERE f.ticker = :ticker")
  List<UUID> findDistinctUserIdsByTicker(@Param("ticker") String ticker);

  Optional<Favorite> findByIdAndFolderId(Long id, Long folderId);

  boolean existsByFolderIdAndTicker(Long folderId, String ticker);

  boolean existsByFolderIdAndTickerAndIdNot(Long folderId, String ticker, Long excludeId);
}
