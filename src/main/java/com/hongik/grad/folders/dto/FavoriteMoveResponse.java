package com.hongik.grad.folders.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** §3-9 PUT /folders/items/move response: item_id/from_folder_id/to_folder_id only. */
public record FavoriteMoveResponse(
    @JsonProperty("item_id") Long itemId,
    @JsonProperty("from_folder_id") Long fromFolderId,
    @JsonProperty("to_folder_id") Long toFolderId) {}
