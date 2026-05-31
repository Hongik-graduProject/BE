package com.hongik.grad.folders.dto;

import com.hongik.grad.folders.domain.Folder;
import com.hongik.grad.folders.domain.FolderType;

/** Folder header for §3-6 — id/name/color/type only (no order/created_at). */
public record FolderBriefResponse(Long id, String name, String color, FolderType type) {

  public static FolderBriefResponse from(Folder folder) {
    return new FolderBriefResponse(
        folder.getId(), folder.getName(), folder.getColor(), folder.getType());
  }
}
