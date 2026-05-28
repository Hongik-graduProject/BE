package com.hongik.grad.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/** Source disclosure reference included in report detail — spec §5-3. */
public record SourceDisclosureResponse(
    @JsonProperty("id") Long id,
    @JsonProperty("title") String title,
    @JsonProperty("filed_at") OffsetDateTime filedAt,
    @JsonProperty("dart_url") String dartUrl) {}
