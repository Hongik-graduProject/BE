package com.hongik.grad.disclosures.event;

import com.hongik.grad.disclosures.domain.DisclosureType;
import java.time.OffsetDateTime;

public record DisclosureCreatedEvent(
    Long disclosureId,
    String ticker,
    String dartFilingId,
    String title,
    DisclosureType type,
    OffsetDateTime filedAt) {}
