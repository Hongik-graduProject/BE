package com.hongik.grad.disclosures.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hongik.grad.disclosures.client.dto.DartListItem;
import com.hongik.grad.disclosures.domain.Disclosure;
import com.hongik.grad.disclosures.domain.DisclosureRepository;
import com.hongik.grad.disclosures.domain.DisclosureType;
import com.hongik.grad.disclosures.event.DisclosureCreatedEvent;
import com.hongik.grad.stocks.domain.Market;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.stocks.domain.StockRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class DisclosureServiceEventTest {

  private DisclosureRepository disclosureRepository;
  private StockRepository stockRepository;
  private ApplicationEventPublisher eventPublisher;
  private DisclosureService disclosureService;

  @BeforeEach
  void setUp() {
    disclosureRepository = mock(DisclosureRepository.class);
    stockRepository = mock(StockRepository.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    disclosureService =
        new DisclosureService(disclosureRepository, stockRepository, eventPublisher);
  }

  @Test
  @DisplayName("신규 공시 INSERT 시 DisclosureCreatedEvent를 발행한다")
  void upsertBatch_publishesEventForNewDisclosure() {
    when(disclosureRepository.findDartFilingIdByDartFilingIdIn(any())).thenReturn(List.of());
    Stock stock = new Stock("005930", "삼성전자", Market.KOSPI);
    when(stockRepository.getReferenceById("005930")).thenReturn(stock);
    when(disclosureRepository.save(any(Disclosure.class)))
        .thenAnswer(
            invocation -> {
              return invocation.getArgument(0);
            });

    DartListItem item =
        new DartListItem(
            "00126380", "삼성전자", "005930", "사업보고서", "20240101000001", "삼성전자", "20240101", "");

    int count = disclosureService.upsertBatch(List.of(item));

    assertThat(count).isEqualTo(1);
    ArgumentCaptor<DisclosureCreatedEvent> captor =
        ArgumentCaptor.forClass(DisclosureCreatedEvent.class);
    verify(eventPublisher, times(1)).publishEvent(captor.capture());
    DisclosureCreatedEvent event = captor.getValue();
    assertThat(event.ticker()).isEqualTo("005930");
    assertThat(event.dartFilingId()).isEqualTo("20240101000001");
    assertThat(event.title()).isEqualTo("사업보고서");
    assertThat(event.type()).isEqualTo(DisclosureType.REPORT);
  }

  @Test
  @DisplayName("이미 존재하는 공시는 이벤트를 발행하지 않는다")
  void upsertBatch_skipsEventForExistingDisclosure() {
    when(disclosureRepository.findDartFilingIdByDartFilingIdIn(any()))
        .thenReturn(List.of("20240101000001"));

    DartListItem item =
        new DartListItem(
            "00126380", "삼성전자", "005930", "사업보고서", "20240101000001", "삼성전자", "20240101", "");

    int count = disclosureService.upsertBatch(List.of(item));

    assertThat(count).isZero();
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("여러 신규 공시가 있으면 각각 이벤트를 발행한다")
  void upsertBatch_publishesMultipleEvents() {
    when(disclosureRepository.findDartFilingIdByDartFilingIdIn(any())).thenReturn(List.of());
    Stock stock = new Stock("005930", "삼성전자", Market.KOSPI);
    when(stockRepository.getReferenceById("005930")).thenReturn(stock);
    when(disclosureRepository.save(any(Disclosure.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    DartListItem item1 =
        new DartListItem(
            "00126380", "삼성전자", "005930", "사업보고서", "20240101000001", "삼성전자", "20240101", "");
    DartListItem item2 =
        new DartListItem(
            "00126380", "삼성전자", "005930", "분기보고서", "20240101000002", "삼성전자", "20240101", "");

    int count = disclosureService.upsertBatch(List.of(item1, item2));

    assertThat(count).isEqualTo(2);
    verify(eventPublisher, times(2)).publishEvent(any(DisclosureCreatedEvent.class));
  }
}
