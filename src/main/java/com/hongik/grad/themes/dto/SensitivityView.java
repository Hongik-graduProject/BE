package com.hongik.grad.themes.dto;

import com.hongik.grad.themes.domain.Direction;

public record SensitivityView(
    String metricCode, double beta, double absBeta, int rank, Direction direction) {}
