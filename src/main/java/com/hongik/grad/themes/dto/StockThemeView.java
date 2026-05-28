package com.hongik.grad.themes.dto;

public record StockThemeView(
    Long themeId, String themeName, short rank, double probability, boolean isPrimary) {}
