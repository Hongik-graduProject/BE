package com.hongik.grad.disclosures.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hongik.grad.disclosures.client.DartClient;
import com.hongik.grad.disclosures.client.dto.DartListItem;
import com.hongik.grad.disclosures.domain.Disclosure;
import com.hongik.grad.disclosures.domain.DisclosureRepository;
import com.hongik.grad.disclosures.domain.DisclosureType;
import com.hongik.grad.stocks.domain.Market;
import com.hongik.grad.stocks.domain.Stock;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DartPollerTest {

  @Mock private DartClient dartClient;
  @Mock private DisclosureService disclosureService;
  @Mock private DisclosureRepository disclosureRepository;

  private final Clock clock = Clock.fixed(Instant.parse("2025-05-27T00:00:00Z"), ZoneOffset.UTC);

  private DartPoller dartPoller;

  @BeforeEach
  void setUp() {
    dartPoller = new DartPoller(dartClient, disclosureService, disclosureRepository, clock);
  }

  @Test
  @DisplayName("기존 공시가 없으면 7일 전 날짜로 DartClient를 호출한다")
  void pollWithNoExistingDisclosures() {
    when(disclosureRepository.findTopByOrderByFiledAtDesc()).thenReturn(Optional.empty());
    when(dartClient.fetchDisclosures(any(), eq(100))).thenReturn(List.of());

    dartPoller.poll();

    verify(dartClient).fetchDisclosures(any(), eq(100));
    verify(disclosureService).upsertBatch(List.of());
  }

  @Test
  @DisplayName("기존 공시가 있으면 마지막 filedAt 날짜로 DartClient를 호출한다")
  void pollWithExistingDisclosures() {
    OffsetDateTime lastFiledAt =
        OffsetDateTime.of(2025, 5, 20, 9, 0, 0, 0, ZoneOffset.of("+09:00"));
    Disclosure latest =
        new Disclosure(
            new Stock("005930", "삼성전자", Market.KOSPI),
            "20250520000001",
            "사업보고서",
            DisclosureType.REPORT,
            lastFiledAt);
    when(disclosureRepository.findTopByOrderByFiledAtDesc()).thenReturn(Optional.of(latest));
    when(dartClient.fetchDisclosures(lastFiledAt.toLocalDate(), 100)).thenReturn(List.of());

    dartPoller.poll();

    verify(dartClient).fetchDisclosures(lastFiledAt.toLocalDate(), 100);
  }

  @Test
  @DisplayName("3건의 공시를 가져오면 upsertBatch에 3건을 전달한다")
  void pollWithItems() {
    when(disclosureRepository.findTopByOrderByFiledAtDesc()).thenReturn(Optional.empty());
    List<DartListItem> items =
        List.of(
            new DartListItem(
                "00126380", "삼성전자", "005930", "사업보고서", "20250520000001", "삼성전자", "20250520", ""),
            new DartListItem(
                "00126380", "삼성전자", "005930", "분기보고서", "20250520000002", "삼성전자", "20250520", ""),
            new DartListItem(
                "00126380", "삼성전자", "005930", "주요사항보고서", "20250520000003", "삼성전자", "20250520", ""));
    when(dartClient.fetchDisclosures(any(), eq(100))).thenReturn(items);
    when(disclosureService.upsertBatch(items)).thenReturn(3);

    dartPoller.poll();

    verify(disclosureService).upsertBatch(items);
  }

  @Test
  @DisplayName("DartClient 예외 발생 시 poll이 중단되지 않는다")
  void pollHandlesExceptionGracefully() {
    when(disclosureRepository.findTopByOrderByFiledAtDesc()).thenReturn(Optional.empty());
    when(dartClient.fetchDisclosures(any(), anyInt())).thenThrow(new RuntimeException("API error"));

    dartPoller.poll();

    verify(disclosureService, never()).upsertBatch(anyList());
  }
}
