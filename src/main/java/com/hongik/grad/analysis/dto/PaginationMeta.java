package com.hongik.grad.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Pagination metadata for analysis list endpoints — spec §5-2. 1-based page numbering. */
public record PaginationMeta(
    @JsonProperty("page") int page,
    @JsonProperty("size") int size,
    @JsonProperty("total_count") long totalCount,
    @JsonProperty("total_pages") int totalPages,
    @JsonProperty("has_next") boolean hasNext) {

  public static PaginationMeta of(int oneBased, int size, long totalCount) {
    if (size < 1) {
      throw new IllegalArgumentException("size must be >= 1");
    }
    int totalPages = (int) Math.ceil((double) totalCount / size);
    boolean hasNext = oneBased < totalPages;
    return new PaginationMeta(oneBased, size, totalCount, totalPages, hasNext);
  }
}
