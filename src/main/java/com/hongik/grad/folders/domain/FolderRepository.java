package com.hongik.grad.folders.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FolderRepository extends JpaRepository<Folder, Long> {

  Optional<Folder> findByIdAndUserId(Long id, UUID userId);

  Optional<Folder> findByUserIdAndType(UUID userId, FolderType type);

  boolean existsByUserIdAndType(UUID userId, FolderType type);

  boolean existsByUserIdAndNameAndType(UUID userId, String name, FolderType type);

  boolean existsByUserIdAndNameAndTypeAndIdNot(UUID userId, String name, FolderType type, Long id);

  int countByUserId(UUID userId);

  List<Folder> findAllByUserIdAndIdIn(UUID userId, Collection<Long> ids);

  List<Folder> findByUserIdOrderByDisplayOrderAsc(UUID userId);
}
