package com.hongik.grad.folders.api;

import com.hongik.grad.auth.principal.AuthUser;
import com.hongik.grad.common.api.ApiResponse;
import com.hongik.grad.folders.application.FavoriteService;
import com.hongik.grad.folders.application.FolderService;
import com.hongik.grad.folders.dto.CreateFolderRequest;
import com.hongik.grad.folders.dto.FolderItemsResponse;
import com.hongik.grad.folders.dto.FolderListWrapper;
import com.hongik.grad.folders.dto.FolderResponse;
import com.hongik.grad.folders.dto.FolderUpdateResponse;
import com.hongik.grad.folders.dto.ReorderFoldersRequest;
import com.hongik.grad.folders.dto.UpdateFolderRequest;
import jakarta.validation.Valid;
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
  public ApiResponse<FolderUpdateResponse> update(
      @AuthenticationPrincipal AuthUser user,
      @PathVariable("folder_id") Long folderId,
      @Valid @RequestBody UpdateFolderRequest request) {
    return ApiResponse.success(folderService.update(user.id(), folderId, request));
  }

  @DeleteMapping("/{folder_id}")
  public ApiResponse<Void> delete(
      @AuthenticationPrincipal AuthUser user, @PathVariable("folder_id") Long folderId) {
    folderService.delete(user.id(), folderId);
    return ApiResponse.success(null, "폴더와 내부 관심 종목이 모두 삭제되었습니다");
  }

  @PutMapping("/order")
  public ApiResponse<Void> reorder(
      @AuthenticationPrincipal AuthUser user, @Valid @RequestBody ReorderFoldersRequest request) {
    folderService.reorder(user.id(), request);
    return ApiResponse.success(null, "폴더 순서가 변경되었습니다");
  }

  @GetMapping
  public ApiResponse<FolderListWrapper> list(@AuthenticationPrincipal AuthUser user) {
    return ApiResponse.success(new FolderListWrapper(folderService.list(user.id())));
  }

  @GetMapping("/{folder_id}/items")
  public ApiResponse<FolderItemsResponse> listItems(
      @AuthenticationPrincipal AuthUser user, @PathVariable("folder_id") Long folderId) {
    return ApiResponse.success(favoriteService.list(user.id(), folderId));
  }
}
