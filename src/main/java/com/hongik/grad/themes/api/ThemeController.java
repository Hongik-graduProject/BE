package com.hongik.grad.themes.api;

import com.hongik.grad.common.api.ApiResponse;
import com.hongik.grad.themes.application.ThemeExploreService;
import com.hongik.grad.themes.dto.ThemeListResponse;
import com.hongik.grad.themes.dto.ThemeStocksResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/themes")
public class ThemeController {

  private final ThemeExploreService themeExploreService;

  public ThemeController(ThemeExploreService themeExploreService) {
    this.themeExploreService = themeExploreService;
  }

  @GetMapping
  public ApiResponse<ThemeListResponse> themes() {
    return ApiResponse.success(themeExploreService.themes());
  }

  @GetMapping("/{theme_id}/stocks")
  public ApiResponse<ThemeStocksResponse> stocks(
      @PathVariable("theme_id") long themeId,
      @RequestParam(value = "page", defaultValue = "1") int page,
      @RequestParam(value = "size", defaultValue = "20") int size) {
    return ApiResponse.success(themeExploreService.stocks(themeId, page, size));
  }
}
