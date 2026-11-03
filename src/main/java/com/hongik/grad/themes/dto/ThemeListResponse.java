package com.hongik.grad.themes.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** GET /themes 응답. */
public record ThemeListResponse(@JsonProperty("themes") List<ThemeSummaryResponse> themes) {}
