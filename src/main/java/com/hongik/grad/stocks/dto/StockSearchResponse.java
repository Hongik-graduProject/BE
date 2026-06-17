package com.hongik.grad.stocks.dto;

import com.hongik.grad.analysis.dto.PaginationMeta;
import java.util.List;

public record StockSearchResponse(List<StockBriefItem> items, PaginationMeta pagination) {}
