package com.hongik.grad.users.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

  /** Callers must pass an already-normalized (lowercased) email. */
  Optional<Profile> findByEmail(String email);

  boolean existsByEmail(String email);
}
