package com.hongik.grad.folders.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record FavoriteMoveRequest(
    @NotNull @JsonProperty("item_id") Long itemId,
    @NotNull @JsonProperty("to_folder_id") Long targetFolderId) {}
