package com.hongik.grad.folders.api;

import com.hongik.grad.auth.principal.AuthUser;
import com.hongik.grad.common.api.ApiResponse;
import com.hongik.grad.folders.application.FavoriteService;
import com.hongik.grad.folders.dto.FavoriteMoveRequest;
import com.hongik.grad.folders.dto.FavoriteMoveResponse;
import com.hongik.grad.folders.dto.FavoriteUpdateRequest;
import com.hongik.grad.folders.dto.FavoriteUpdateResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/folders/items")
public class FavoriteController {

  private final FavoriteService favoriteService;

  public FavoriteController(FavoriteService favoriteService) {
    this.favoriteService = favoriteService;
  }

  @PatchMapping("/{item_id}")
  public ApiResponse<FavoriteUpdateResponse> update(
      @AuthenticationPrincipal AuthUser user,
      @PathVariable("item_id") Long itemId,
      @RequestBody FavoriteUpdateRequest request) {
    return ApiResponse.success(favoriteService.update(user.id(), itemId, request));
  }

  @DeleteMapping("/{item_id}")
  public ApiResponse<Void> delete(
      @AuthenticationPrincipal AuthUser user, @PathVariable("item_id") Long itemId) {
    favoriteService.delete(user.id(), itemId);
    return ApiResponse.success(null, "관심 종목이 삭제되었습니다");
  }

  @PutMapping("/move")
  public ApiResponse<FavoriteMoveResponse> move(
      @AuthenticationPrincipal AuthUser user, @Valid @RequestBody FavoriteMoveRequest request) {
    return ApiResponse.success(favoriteService.move(user.id(), request.itemId(), request));
  }
}
