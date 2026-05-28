package com.hongik.grad.themes.etl.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ThemeCatalogJson(
    @JsonProperty("class_idx") int classIdx,
    @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("source") String source) {}
