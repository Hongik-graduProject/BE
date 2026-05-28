package com.hongik.grad.analysis.listener;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hongik.grad.analysis.application.AnalysisReportGenerator;
import com.hongik.grad.disclosures.domain.DisclosureType;
import com.hongik.grad.disclosures.event.DisclosureCreatedEvent;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DisclosureCreatedListenerTest {

  @Mock private AnalysisReportGenerator generator;

  private DisclosureCreatedListener listener;

  @BeforeEach
  void setUp() {
    listener = new DisclosureCreatedListener(generator);
  }

  @Test
  @DisplayName("event 수신 시 disclosureId로 generate(Long) 호출")
  void onDisclosureCreated_invokesGeneratorWithId() throws Exception {
    when(generator.generate(eq(100L))).thenReturn(Optional.empty());

    DisclosureCreatedEvent event =
        new DisclosureCreatedEvent(
            100L, "005930", "20240101000001", "사업보고서", DisclosureType.REPORT, OffsetDateTime.now());
    listener.onDisclosureCreated(event);

    verify(generator, times(1)).generate(100L);
  }

  @Test
  @DisplayName("generator가 예외를 던져도 listener는 swallow 한다 (다른 이벤트 영향 X)")
  void onDisclosureCreated_swallowsException() throws Exception {
    when(generator.generate(eq(200L))).thenThrow(new IllegalStateException("boom"));

    DisclosureCreatedEvent event =
        new DisclosureCreatedEvent(
            200L, "005930", "20240101000099", "사업보고서", DisclosureType.REPORT, OffsetDateTime.now());

    listener.onDisclosureCreated(event);
    verify(generator, times(1)).generate(200L);
  }
}
