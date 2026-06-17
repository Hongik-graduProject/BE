package com.hongik.grad.stocks.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecentSearchAddRequest(@NotBlank @Size(max = 100) String keyword) {}
