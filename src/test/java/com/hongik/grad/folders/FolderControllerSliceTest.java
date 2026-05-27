package com.hongik.grad.folders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongik.grad.auth.filter.SupabaseJwtAuthenticationFilter;
import com.hongik.grad.auth.handler.JsonAccessDeniedHandler;
import com.hongik.grad.auth.handler.JsonAuthenticationEntryPoint;
import com.hongik.grad.auth.provider.SupabaseJwtVerifier;
import com.hongik.grad.common.api.ErrorCode;
import com.hongik.grad.common.exception.DomainException;
import com.hongik.grad.folders.api.FolderController;
import com.hongik.grad.folders.application.FavoriteService;
import com.hongik.grad.folders.application.FolderService;
import com.hongik.grad.folders.domain.FolderType;
import com.hongik.grad.folders.dto.AnalysisSummary;
import com.hongik.grad.folders.dto.FavoriteItemResponse;
import com.hongik.grad.folders.dto.FolderListResponse;
import com.hongik.grad.folders.dto.FolderResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.hongik.grad.support.JwtTestFactory;
import com.hongik.grad.config.SecurityConfig;

@WebMvcTest(FolderController.class)
@Import({
  SecurityConfig.class,
  SupabaseJwtVerifier.class,
  SupabaseJwtAuthenticationFilter.class,
  JsonAuthenticationEntryPoint.class,
  JsonAccessDeniedHandler.class
})
@ActiveProfiles("test")
class FolderControllerSliceTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private FolderService folderService;
  @MockBean private FavoriteService favoriteService;

  private static final UUID USER_ID = UUID.randomUUID();
  private static final String TOKEN = "Bearer " + JwtTestFactory.validToken(USER_ID, "test@test.com");

  // ── POST /folders ────────────────────────────────────────────────────────

  @Test
  @DisplayName("POST /folders - 성공 시 201과 FolderResponse 반환")
  void createFolder_success() throws Exception {
    FolderResponse response =
        new FolderResponse(1L, "테크", "#FF0000", FolderType.CUSTOM, 1, OffsetDateTime.now());
    given(folderService.create(eq(USER_ID), any())).willReturn(response);

    mockMvc
        .perform(
            post("/folders")
                .header(HttpHeaders.AUTHORIZATION, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "테크", "color", "#FF0000"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("success"))
        .andExpect(jsonPath("$.data.name").value("테크"))
        .andExpect(jsonPath("$.data.color").value("#FF0000"))
        .andExpect(jsonPath("$.data.type").value("CUSTOM"));
  }

  @Test
  @DisplayName("POST /folders - 토큰 없으면 401")
  void createFolder_noToken_401() throws Exception {
    mockMvc
        .perform(
            post("/folders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "테크", "color", "#FF0000"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("AUTH_INVALID"));
  }

  @Test
  @DisplayName("POST /folders - name 공백이면 400 INVALID_PARAM")
  void createFolder_blankName_400() throws Exception {
    mockMvc
        .perform(
            post("/folders")
                .header(HttpHeaders.AUTHORIZATION, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "", "color", "#FF0000"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_PARAM"));
  }

  @Test
  @DisplayName("POST /folders - 잘못된 color 형식이면 400 INVALID_PARAM")
  void createFolder_invalidColor_400() throws Exception {
    mockMvc
        .perform(
            post("/folders")
                .header(HttpHeaders.AUTHORIZATION, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "테크", "color", "red"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_PARAM"));
  }

  @Test
  @DisplayName("POST /folders - 중복 이름이면 409 DUPLICATE")
  void createFolder_duplicate_409() throws Exception {
    given(folderService.create(eq(USER_ID), any()))
        .willThrow(new DomainException(ErrorCode.DUPLICATE, "같은 이름의 폴더가 이미 존재합니다"));

    mockMvc
        .perform(
            post("/folders")
                .header(HttpHeaders.AUTHORIZATION, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "테크", "color", "#FF0000"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("DUPLICATE"));
  }

  // ── PATCH /folders/{folder_id} ───────────────────────────────────────────

  @Test
  @DisplayName("PATCH /folders/{folder_id} - 성공 시 200과 FolderResponse 반환")
  void updateFolder_success() throws Exception {
    FolderResponse response =
        new FolderResponse(1L, "수정됨", "#00FF00", FolderType.CUSTOM, 0, OffsetDateTime.now());
    given(folderService.update(eq(USER_ID), eq(1L), any())).willReturn(response);

    mockMvc
        .perform(
            patch("/folders/1")
                .header(HttpHeaders.AUTHORIZATION, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("name", "수정됨", "color", "#00FF00"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("success"))
        .andExpect(jsonPath("$.data.name").value("수정됨"));
  }

  @Test
  @DisplayName("PATCH /folders/{folder_id} - 기본 폴더 수정 시 403 FORBIDDEN")
  void updateFolder_defaultFolder_403() throws Exception {
    given(folderService.update(eq(USER_ID), eq(1L), any()))
        .willThrow(new DomainException(ErrorCode.FORBIDDEN, "기본 폴더는 수정할 수 없습니다"));

    mockMvc
        .perform(
            patch("/folders/1")
                .header(HttpHeaders.AUTHORIZATION, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("name", "수정됨", "color", "#00FF00"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("FORBIDDEN"));
  }

  @Test
  @DisplayName("PATCH /folders/{folder_id} - 존재하지 않는 폴더면 404 NOT_FOUND")
  void updateFolder_notFound_404() throws Exception {
    given(folderService.update(eq(USER_ID), eq(99L), any()))
        .willThrow(new DomainException(ErrorCode.NOT_FOUND, "폴더를 찾을 수 없습니다"));

    mockMvc
        .perform(
            patch("/folders/99")
                .header(HttpHeaders.AUTHORIZATION, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("name", "수정됨", "color", "#00FF00"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  // ── DELETE /folders/{folder_id} ──────────────────────────────────────────

  @Test
  @DisplayName("DELETE /folders/{folder_id} - 성공 시 204 반환")
  void deleteFolder_success() throws Exception {
    willDoNothing().given(folderService).delete(eq(USER_ID), eq(1L));

    mockMvc
        .perform(delete("/folders/1").header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("DELETE /folders/{folder_id} - 기본 폴더 삭제 시 403 FORBIDDEN")
  void deleteFolder_defaultFolder_403() throws Exception {
    willThrow(new DomainException(ErrorCode.FORBIDDEN, "기본 폴더는 삭제할 수 없습니다"))
        .given(folderService)
        .delete(eq(USER_ID), eq(1L));

    mockMvc
        .perform(delete("/folders/1").header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("FORBIDDEN"));
  }

  // ── PUT /folders/order ───────────────────────────────────────────────────

  @Test
  @DisplayName("PUT /folders/order - 성공 시 204 반환")
  void reorderFolders_success() throws Exception {
    willDoNothing().given(folderService).reorder(eq(USER_ID), any());

    mockMvc
        .perform(
            put("/folders/order")
                .header(HttpHeaders.AUTHORIZATION, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("folder_ids", List.of(2L, 1L, 3L)))))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("PUT /folders/order - folderIds 비어있으면 400 INVALID_PARAM")
  void reorderFolders_emptyList_400() throws Exception {
    mockMvc
        .perform(
            put("/folders/order")
                .header(HttpHeaders.AUTHORIZATION, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("folder_ids", List.of()))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_PARAM"));
  }

  @Test
  @DisplayName("PUT /folders/order - 타인 폴더 포함 시 403 FORBIDDEN")
  void reorderFolders_foreignFolder_403() throws Exception {
    willThrow(new DomainException(ErrorCode.FORBIDDEN, "본인 폴더만 재정렬할 수 있습니다"))
        .given(folderService)
        .reorder(eq(USER_ID), any());

    mockMvc
        .perform(
            put("/folders/order")
                .header(HttpHeaders.AUTHORIZATION, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("folder_ids", List.of(1L, 999L)))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("FORBIDDEN"));
  }

  // ── GET /folders ─────────────────────────────────────────────────────────

  @Test
  @DisplayName("GET /folders - 성공 시 200과 폴더 목록 반환 (item_count, unread_disclosure_count 포함)")
  void listFolders_success() throws Exception {
    FolderListResponse r1 =
        new FolderListResponse(
            1L, "기본 폴더", "#999999", FolderType.DEFAULT, 0, OffsetDateTime.now(), 3, 0);
    FolderListResponse r2 =
        new FolderListResponse(
            2L, "테크", "#FF0000", FolderType.CUSTOM, 1, OffsetDateTime.now(), 1, 0);
    given(folderService.list(USER_ID)).willReturn(List.of(r1, r2));

    mockMvc
        .perform(get("/folders").header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("success"))
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].item_count").value(3))
        .andExpect(jsonPath("$.data[0].unread_disclosure_count").value(0))
        .andExpect(jsonPath("$.data[1].name").value("테크"));
  }

  @Test
  @DisplayName("GET /folders - 토큰 없으면 401")
  void listFolders_noToken_401() throws Exception {
    mockMvc.perform(get("/folders")).andExpect(status().isUnauthorized());
  }

  // ── GET /folders/{folder_id}/items ───────────────────────────────────────

  @Test
  @DisplayName("GET /folders/{folder_id}/items - 성공 시 200과 항목 목록 반환")
  void listFolderItems_success() throws Exception {
    FavoriteItemResponse item =
        new FavoriteItemResponse(
            10L,
            "005930",
            "장기보유",
            70000L,
            null,
            null,
            null,
            new AnalysisSummary(false, "테마 분류 대기 중"),
            OffsetDateTime.now());
    given(favoriteService.list(USER_ID, 1L)).willReturn(List.of(item));

    mockMvc
        .perform(get("/folders/1/items").header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("success"))
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].ticker").value("005930"))
        .andExpect(jsonPath("$.data[0].avg_price").value(70000))
        .andExpect(jsonPath("$.data[0].analysis_summary.is_analyzed").value(false));
  }

  @Test
  @DisplayName("GET /folders/{folder_id}/items - 존재하지 않는 폴더면 404 NOT_FOUND")
  void listFolderItems_notFound_404() throws Exception {
    given(favoriteService.list(USER_ID, 99L))
        .willThrow(new DomainException(ErrorCode.NOT_FOUND, "폴더를 찾을 수 없습니다"));

    mockMvc
        .perform(get("/folders/99/items").header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("GET /folders/{folder_id}/items - 타인 폴더 접근 시 403 FORBIDDEN")
  void listFolderItems_forbidden_403() throws Exception {
    given(favoriteService.list(USER_ID, 2L))
        .willThrow(new DomainException(ErrorCode.FORBIDDEN, "해당 폴더에 접근 권한이 없습니다"));

    mockMvc
        .perform(get("/folders/2/items").header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("FORBIDDEN"));
  }
}
