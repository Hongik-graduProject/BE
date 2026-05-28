package com.hongik.grad.themes.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThemeRepository extends JpaRepository<Theme, Long> {

  Optional<Theme> findByName(String name);
}
