package com.hongik.grad.disclosures.application;

import com.hongik.grad.disclosures.client.dto.DartListItem;
import com.hongik.grad.disclosures.domain.Disclosure;
import com.hongik.grad.disclosures.domain.DisclosureRepository;
import com.hongik.grad.disclosures.domain.DisclosureType;
import com.hongik.grad.disclosures.event.DisclosureCreatedEvent;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.stocks.domain.StockRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DisclosureService {

  private static final Logger log = LoggerFactory.getLogger(DisclosureService.class);
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

  private final DisclosureRepository disclosureRepository;
  private final StockRepository stockRepository;
  private final ApplicationEventPublisher eventPublisher;

  public DisclosureService(
      DisclosureRepository disclosureRepository,
      StockRepository stockRepository,
      ApplicationEventPublisher eventPublisher) {
    this.disclosureRepository = disclosureRepository;
    this.stockRepository = stockRepository;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public int upsertBatch(List<DartListItem> items) {
    if (items.isEmpty()) {
      return 0;
    }

    Set<String> existingIds =
        new HashSet<>(
            disclosureRepository.findDartFilingIdByDartFilingIdIn(
                items.stream().map(DartListItem::rceptNo).toList()));

    int newCount = 0;
    for (DartListItem item : items) {
      if (existingIds.contains(item.rceptNo())) {
        continue;
      }
      Stock stock = stockRepository.getReferenceById(item.stockCode());
      OffsetDateTime filedAt = parseFiledAt(item.rceptDt());
      DisclosureType type = mapDisclosureType(item.reportNm());

      Disclosure disclosure =
          new Disclosure(stock, item.rceptNo(), item.reportNm(), type, filedAt);
      disclosureRepository.save(disclosure);
      // Consumers must use @TransactionalEventListener(phase = AFTER_COMMIT)
      // to ensure they only see committed data (this is the pattern used in PR-08).
      eventPublisher.publishEvent(
          new DisclosureCreatedEvent(
              disclosure.getId(),
              item.stockCode(),
              item.rceptNo(),
              item.reportNm(),
              type,
              filedAt));
      newCount++;
    }
    return newCount;
  }

  private OffsetDateTime parseFiledAt(String rceptDt) {
    LocalDate date = LocalDate.parse(rceptDt, DATE_FMT);
    return OffsetDateTime.of(date, LocalTime.MIDNIGHT, ZoneOffset.of("+09:00"));
  }

  private DisclosureType mapDisclosureType(String reportNm) {
    if (reportNm.contains("분기")) {
      return DisclosureType.QUARTERLY;
    }
    if (reportNm.contains("반기")) {
      return DisclosureType.INTERIM;
    }
    if (reportNm.contains("사업")) {
      return DisclosureType.REPORT;
    }
    return DisclosureType.EVENT;
  }
}
