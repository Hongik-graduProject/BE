package com.hongik.grad.folders.api;

import com.hongik.grad.auth.principal.AuthUser;
import com.hongik.grad.common.api.ApiResponse;
import com.hongik.grad.folders.application.FavoriteService;
import com.hongik.grad.folders.application.FolderService;
import com.hongik.grad.folders.dto.CreateFolderRequest;
import com.hongik.grad.folders.dto.FavoriteItemResponse;
import com.hongik.grad.folders.dto.FolderListResponse;
import com.hongik.grad.folders.dto.FolderResponse;
import com.hongik.grad.folders.dto.ReorderFoldersRequest;
import com.hongik.grad.folders.dto.UpdateFolderRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/folders")
public class FolderController {

  private final FolderService folderService;
  private final FavoriteService favoriteService;

  public FolderController(FolderService folderService, FavoriteService favoriteService) {
    this.folderService = folderService;
    this.favoriteService = favoriteService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<FolderResponse> create(
      @AuthenticationPrincipal AuthUser user, @Valid @RequestBody CreateFolderRequest request) {
    return ApiResponse.success(folderService.create(user.id(), request));
  }

  @PatchMapping("/{folder_id}")
  public ApiResponse<FolderResponse> update(
      @AuthenticationPrincipal AuthUser user,
      @PathVariable("folder_id") Long folderId,
      @Valid @RequestBody UpdateFolderRequest request) {
    return ApiResponse.success(folderService.update(user.id(), folderId, request));
  }

  @DeleteMapping("/{folder_id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @AuthenticationPrincipal AuthUser user, @PathVariable("folder_id") Long folderId) {
    folderService.delete(user.id(), folderId);
  }

  @PutMapping("/order")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reorder(
      @AuthenticationPrincipal AuthUser user, @Valid @RequestBody ReorderFoldersRequest request) {
    folderService.reorder(user.id(), request);
  }

  @GetMapping
  public ApiResponse<List<FolderListResponse>> list(@AuthenticationPrincipal AuthUser user) {
    return ApiResponse.success(folderService.list(user.id()));
  }

  @GetMapping("/{folder_id}/items")
  public ApiResponse<List<FavoriteItemResponse>> listItems(
      @AuthenticationPrincipal AuthUser user, @PathVariable("folder_id") Long folderId) {
    return ApiResponse.success(favoriteService.list(user.id(), folderId));
  }
}
