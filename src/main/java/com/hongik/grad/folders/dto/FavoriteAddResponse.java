package com.hongik.grad.folders.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/** §3-7 POST /folders/items 응답. */
public record FavoriteAddResponse(
    Long id,
    @JsonProperty("folder_id") Long folderId,
    String ticker,
    @JsonProperty("added_at") OffsetDateTime addedAt) {}
