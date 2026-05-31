package com.hongik.grad.notifications.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/** Single notification entry — spec §6-1. */
public record NotificationItemResponse(
    @JsonProperty("id") Long id,
    @JsonProperty("type") String type,
    @JsonProperty("title") String title,
    @JsonProperty("body") String body,
    @JsonProperty("ticker") String ticker,
    @JsonProperty("stock_name") String stockName,
    @JsonProperty("report_id") Long reportId,
    @JsonProperty("is_read") boolean isRead,
    @JsonProperty("created_at") OffsetDateTime createdAt) {}
