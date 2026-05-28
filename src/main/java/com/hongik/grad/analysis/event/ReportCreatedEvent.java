package com.hongik.grad.analysis.event;

import java.time.OffsetDateTime;

/** Emitted after an {@link com.hongik.grad.analysis.domain.AnalysisReport} is persisted. */
public record ReportCreatedEvent(
    Long reportId, String ticker, Long appliedThemeId, String title, OffsetDateTime generatedAt) {}
