package com.hongik.grad.folders.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** §3-7 POST /folders/items — 종목을 폴더에 즐겨찾기로 등록. */
public record FavoriteAddRequest(
    @NotNull @JsonProperty("folder_id") Long folderId,
    @NotBlank @Pattern(regexp = "^[A-Za-z0-9]{1,10}$", message = "ticker must be alphanumeric, ≤10")
        String ticker) {}
