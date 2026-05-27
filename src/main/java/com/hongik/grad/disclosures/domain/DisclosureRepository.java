package com.hongik.grad.disclosures.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DisclosureRepository extends JpaRepository<Disclosure, Long> {

  boolean existsByDartFilingId(String dartFilingId);

  @Query("SELECT d.dartFilingId FROM Disclosure d WHERE d.dartFilingId IN :ids")
  List<String> findDartFilingIdByDartFilingIdIn(Collection<String> ids);

  Optional<Disclosure> findByDartFilingId(String dartFilingId);

  Optional<Disclosure> findTopByOrderByFiledAtDesc();
}
