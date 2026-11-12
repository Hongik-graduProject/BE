package com.hongik.grad.radar.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hongik.grad.analysis.event.ReportCreatedEvent;
import com.hongik.grad.disclosures.domain.DisclosureType;
import com.hongik.grad.disclosures.event.DisclosureCreatedEvent;
import com.hongik.grad.radar.application.RadarEmitterRegistry;
import com.hongik.grad.radar.application.RadarService;
import com.hongik.grad.radar.dto.RadarPoint;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RadarEventListenerTest {

  private final RadarService radarService = mock(RadarService.class);
  private final RadarEmitterRegistry registry = mock(RadarEmitterRegistry.class);
  private final RadarEventListener listener = new RadarEventListener(radarService, registry);
  private final UUID user = UUID.randomUUID();
  private final OffsetDateTime now = OffsetDateTime.now();

  private RadarPoint point(String event) {
    return new RadarPoint(
        7L, "005930", "삼성전자", "t", "event", null, 0, 0.85, null, null, now, false, event);
  }

  @Test
  void disclosureEvent_pushesPointToConnectedUser() {
    given(registry.connectedUsers()).willReturn(Set.of(user));
    RadarPoint p = point("disclosure");
    given(radarService.streamPoints(7L, "disclosure", Set.of(user))).willReturn(Map.of(user, p));

    listener.onDisclosureCreated(
        new DisclosureCreatedEvent(7L, "005930", "F", "t", DisclosureType.EVENT, now));

    verify(registry).send(user, "disclosure", p);
  }

  @Test
  void reportEvent_pushesUpdateEvent() {
    given(registry.connectedUsers()).willReturn(Set.of(user));
    RadarPoint p = point("report");
    given(radarService.streamPointsForReport(3L, Set.of(user))).willReturn(Map.of(user, p));

    listener.onReportCreated(new ReportCreatedEvent(3L, "005930", 1L, "t", now));

    verify(registry).send(user, "report", p);
  }

  @Test
  void noConnections_skipsDbWork() {
    given(registry.connectedUsers()).willReturn(Set.of());

    listener.onDisclosureCreated(
        new DisclosureCreatedEvent(7L, "005930", "F", "t", DisclosureType.EVENT, now));

    verifyNoInteractions(radarService);
  }

  @Test
  void failure_isSwallowed() {
    given(registry.connectedUsers()).willReturn(Set.of(user));
    given(radarService.streamPoints(any(), anyString(), any()))
        .willThrow(new IllegalStateException("db down"));

    listener.onDisclosureCreated(
        new DisclosureCreatedEvent(7L, "005930", "F", "t", DisclosureType.EVENT, now));

    verify(registry, never()).send(eq(user), anyString(), any());
  }
}
