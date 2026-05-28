package com.hongik.grad.analysis.routing;

/** Output of {@link ThemeRouter#route}. {@code score} is exposed as theme_routing_score. */
public record RoutingResult(Long themeId, String themeName, double score) {}
