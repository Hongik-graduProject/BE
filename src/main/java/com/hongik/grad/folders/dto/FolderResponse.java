package com.hongik.grad.folders.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hongik.grad.folders.domain.Folder;
import com.hongik.grad.folders.domain.FolderType;
import java.time.OffsetDateTime;

public record FolderResponse(
    Long id,
    String name,
    String color,
    FolderType type,
    @JsonProperty("order") int displayOrder,
    OffsetDateTime createdAt) {

  public static FolderResponse from(Folder folder) {
    return new FolderResponse(
        folder.getId(),
        folder.getName(),
        folder.getColor(),
        folder.getType(),
        folder.getDisplayOrder(),
        folder.getCreatedAt());
  }
}
