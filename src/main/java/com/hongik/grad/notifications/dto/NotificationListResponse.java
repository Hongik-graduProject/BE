package com.hongik.grad.notifications.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hongik.grad.analysis.dto.PaginationMeta;
import java.util.List;

/** Notification list payload — spec §6-1. */
public record NotificationListResponse(
    @JsonProperty("notifications") List<NotificationItemResponse> notifications,
    @JsonProperty("unread_count") long unreadCount,
    @JsonProperty("pagination") PaginationMeta pagination) {}
