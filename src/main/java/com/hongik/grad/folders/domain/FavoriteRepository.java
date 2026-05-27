package com.hongik.grad.folders.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

  List<Favorite> findByFolderIdOrderByAddedAtDesc(Long folderId);

  Optional<Favorite> findByIdAndFolderId(Long id, Long folderId);

  boolean existsByFolderIdAndTicker(Long folderId, String ticker);

  boolean existsByFolderIdAndTickerAndIdNot(Long folderId, String ticker, Long excludeId);

  int countByFolderId(Long folderId);
}
