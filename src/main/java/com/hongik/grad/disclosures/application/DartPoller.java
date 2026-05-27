package com.hongik.grad.disclosures.application;

import com.hongik.grad.disclosures.client.DartClient;
import com.hongik.grad.disclosures.client.dto.DartListItem;
import com.hongik.grad.disclosures.domain.Disclosure;
import com.hongik.grad.disclosures.domain.DisclosureRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DartPoller {

  private static final Logger log = LoggerFactory.getLogger(DartPoller.class);
  private static final int PAGE_COUNT = 100;

  private final DartClient dartClient;
  private final DisclosureService disclosureService;
  private final DisclosureRepository disclosureRepository;
  private final Clock clock;

  public DartPoller(
      DartClient dartClient,
      DisclosureService disclosureService,
      DisclosureRepository disclosureRepository,
      Clock clock) {
    this.dartClient = dartClient;
    this.disclosureService = disclosureService;
    this.disclosureRepository = disclosureRepository;
    this.clock = clock;
  }

  @Scheduled(fixedDelay = 60_000)
  public void poll() {
    try {
      LocalDate since = resolveStartDate();
      List<DartListItem> items = dartClient.fetchDisclosures(since, PAGE_COUNT);
      int newCount = disclosureService.upsertBatch(items);
      log.info("Polled DART: {} new disclosures from {} items", newCount, items.size());
    } catch (Exception ex) {
      log.warn("DART polling failed: {}", ex.getMessage(), ex);
    }
  }

  private LocalDate resolveStartDate() {
    Optional<Disclosure> latest = disclosureRepository.findTopByOrderByFiledAtDesc();
    if (latest.isPresent()) {
      return latest.get().getFiledAt().toLocalDate();
    }
    return OffsetDateTime.now(clock).minusDays(7).toLocalDate();
  }
}
