package com.hongik.grad.folders.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hongik.grad.folders.domain.Folder;
import com.hongik.grad.folders.domain.FolderType;
import java.time.OffsetDateTime;

public record FolderListResponse(
    Long id,
    String name,
    String color,
    FolderType type,
    @JsonProperty("order") int displayOrder,
    OffsetDateTime createdAt,
    @JsonProperty("item_count") int itemCount,
    @JsonProperty("unread_disclosure_count") int unreadDisclosureCount) {

  public static FolderListResponse from(Folder folder, int itemCount, int unreadDisclosureCount) {
    return new FolderListResponse(
        folder.getId(),
        folder.getName(),
        folder.getColor(),
        folder.getType(),
        folder.getDisplayOrder(),
        folder.getCreatedAt(),
        itemCount,
        unreadDisclosureCount);
  }
}
