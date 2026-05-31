package com.hongik.grad.folders.dto;

import com.hongik.grad.folders.domain.Folder;

/** §3-3 PATCH /folders/{folder_id} response: id/name/color only. */
public record FolderUpdateResponse(Long id, String name, String color) {

  public static FolderUpdateResponse from(Folder folder) {
    return new FolderUpdateResponse(folder.getId(), folder.getName(), folder.getColor());
  }
}
