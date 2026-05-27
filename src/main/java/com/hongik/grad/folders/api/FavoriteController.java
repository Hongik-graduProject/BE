package com.hongik.grad.folders.api;

import com.hongik.grad.auth.principal.AuthUser;
import com.hongik.grad.common.api.ApiResponse;
import com.hongik.grad.folders.application.FavoriteService;
import com.hongik.grad.folders.dto.FavoriteItemResponse;
import com.hongik.grad.folders.dto.FavoriteMoveRequest;
import com.hongik.grad.folders.dto.FavoriteUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/folders/items")
public class FavoriteController {

  private final FavoriteService favoriteService;

  public FavoriteController(FavoriteService favoriteService) {
    this.favoriteService = favoriteService;
  }

  @PatchMapping("/{item_id}")
  public ApiResponse<FavoriteItemResponse> update(
      @AuthenticationPrincipal AuthUser user,
      @PathVariable("item_id") Long itemId,
      @RequestBody FavoriteUpdateRequest request) {
    return ApiResponse.success(favoriteService.update(user.id(), itemId, request));
  }

  @DeleteMapping("/{item_id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable("item_id") Long itemId) {
    favoriteService.delete(user.id(), itemId);
  }

  @PutMapping("/move")
  public ApiResponse<FavoriteItemResponse> move(
      @AuthenticationPrincipal AuthUser user, @Valid @RequestBody FavoriteMoveRequest request) {
    return ApiResponse.success(favoriteService.move(user.id(), request.itemId(), request));
  }
}
