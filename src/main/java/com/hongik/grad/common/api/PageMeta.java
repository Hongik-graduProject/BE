package com.hongik.grad.common.api;

/** Pagination metadata returned alongside list payloads. */
public record PageMeta(int page, int size, long total, int totalPages) {

  public static PageMeta of(int page, int size, long total) {
    int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) total / size);
    return new PageMeta(page, size, total, totalPages);
  }
}
