package com.hongik.grad.folders.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FolderRepository extends JpaRepository<Folder, Long> {

  Optional<Folder> findByUserIdAndType(UUID userId, FolderType type);

  boolean existsByUserIdAndType(UUID userId, FolderType type);
}
