package com.hongik.grad.timemachine.domain;

import java.time.LocalDate;

/** daily_prices 한 행. 지수(KOSPI)는 0.01pt 단위 정수다. */
public record PricePoint(LocalDate date, long close) {}
