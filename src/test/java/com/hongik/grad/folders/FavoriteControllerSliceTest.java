package com.hongik.grad.folders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.hongik.grad.config.SecurityConfig;
import com.hongik.grad.folders.api.FavoriteController;
import com.hongik.grad.folders.application.FavoriteService;
import com.hongik.grad.folders.dto.FavoriteMoveResponse;
import com.hongik.grad.folders.dto.FavoriteUpdateResponse;
import com.hongik.grad.support.JwtTestFactory;
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

@WebMvcTest(FavoriteController.class)
@Import({
  SecurityConfig.class,
  SupabaseJwtVerifier.class,
  SupabaseJwtAuthenticationFilter.class,
  JsonAuthenticationEntryPoint.class,
  JsonAccessDeniedHandler.class
})
@ActiveProfiles("test")
class FavoriteControllerSliceTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private FavoriteService favoriteService;

  private static final UUID USER_ID = UUID.randomUUID();
  private static final String TOKEN =
      "Bearer " + JwtTestFactory.validToken(USER_ID, "test@test.com");

  // ── PATCH /folders/items/{item_id} ───────────────────────────────────────

  @Test
  @DisplayName("PATCH /folders/items/{item_id} - memo 수정 성공 시 200과 id/memo/avg_price만 반환")
  void updateFavorite_memo_success() throws Exception {
    given(favoriteService.update(eq(USER_ID), eq(10L), any()))
        .willReturn(new FavoriteUpdateResponse(10L, "단기매매", 70000L));

    mockMvc
        .perform(
            patch("/folders/items/10")
                .header(HttpHeaders.AUTHORIZATION, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("memo", "단기매매"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("success"))
        .andExpect(jsonPath("$.data.id").value(10))
        .andExpect(jsonPath("$.data.memo").value("단기매매"))
        .andExpect(jsonPath("$.data.avg_price").value(70000))
        .andExpect(jsonPath("$.data.ticker").doesNotExist())
        .andExpect(jsonPath("$.data.name").doesNotExist())
        .andExpect(jsonPath("$.data.analysis_summary").doesNotExist());
  }

  @Test
  @DisplayName("PATCH /folders/items/{item_id} - avg_price 수정 성공 시 200 반환")
  void updateFavorite_avgPrice_success() throws Exception {
    given(favoriteService.update(eq(USER_ID), eq(10L), any()))
        .willReturn(new FavoriteUpdateResponse(10L, "장기보유", 80000L));

    mockMvc
        .perform(
            patch("/folders/items/10")
                .header(HttpHeaders.AUTHORIZATION, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("avg_price", 80000))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.avg_price").value(80000));
  }

  @Test
  @DisplayName("PATCH /folders/items/{item_id} - 토큰 없으면 401")
  void updateFavorite_noToken_401() throws Exception {
    mockMvc
        .perform(
            patch("/folders/items/10")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("memo", "test"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("AUTH_INVALID"));
  }

  @Test
  @DisplayName("PATCH /folders/items/{item_id} - 존재하지 않는 항목이면 404 NOT_FOUND")
  void updateFavorite_notFound_404() throws Exception {
    given(favoriteService.update(eq(USER_ID), eq(99L), any()))
        .willThrow(new DomainException(ErrorCode.NOT_FOUND, "즐겨찾기 항목을 찾을 수 없습니다"));

    mockMvc
        .perform(
            patch("/folders/items/99")
                .header(HttpHeaders.AUTHORIZATION, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("memo", "test"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("PATCH /folders/items/{item_id} - 타인 항목 접근 시 403 FORBIDDEN")
  void updateFavorite_forbidden_403() throws Exception {
    given(favoriteService.update(eq(USER_ID), eq(10L), any()))
        .willThrow(new DomainException(ErrorCode.FORBIDDEN, "해당 항목에 접근 권한이 없습니다"));

    mockMvc
        .perform(
            patch("/folders/items/10")
                .header(HttpHeaders.AUTHORIZATION, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("memo", "test"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("FORBIDDEN"));
  }

  // ── DELETE /folders/items/{item_id} ──────────────────────────────────────

  @Test
  @DisplayName("DELETE /folders/items/{item_id} - 성공 시 200과 message 반환")
  void deleteFavorite_success() throws Exception {
    willDoNothing().given(favoriteService).delete(eq(USER_ID), eq(10L));

    mockMvc
        .perform(delete("/folders/items/10").header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("success"))
        .andExpect(jsonPath("$.message").value("관심 종목이 삭제되었습니다"));
  }

  @Test
  @DisplayName("DELETE /folders/items/{item_id} - 토큰 없으면 401")
  void deleteFavorite_noToken_401() throws Exception {
    mockMvc
        .perform(delete("/folders/items/10"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("AUTH_INVALID"));
  }

  @Test
  @DisplayName("DELETE /folders/items/{item_id} - 존재하지 않는 항목이면 404 NOT_FOUND")
  void deleteFavorite_notFound_404() throws Exception {
    willThrow(new DomainException(ErrorCode.NOT_FOUND, "즐겨찾기 항목을 찾을 수 없습니다"))
        .given(favoriteService)
        .delete(eq(USER_ID), eq(99L));

    mockMvc
        .perform(delete("/folders/items/99").header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  // ── PUT /folders/items/move ──────────────────────────────────────────────

  @Test
  @DisplayName("PUT /folders/items/move - 이동 성공 시 200과 item_id/from_folder_id/to_folder_id 반환")
  void moveFavorite_success() throws Exception {
    given(favoriteService.move(eq(USER_ID), eq(10L), any()))
        .willReturn(new FavoriteMoveResponse(10L, 1L, 2L));

    mockMvc
        .perform(
            put("/folders/items/move")
                .header(HttpHeaders.AUTHORIZATION, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("item_id", 10, "to_folder_id", 2))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("success"))
        .andExpect(jsonPath("$.data.item_id").value(10))
        .andExpect(jsonPath("$.data.from_folder_id").value(1))
        .andExpect(jsonPath("$.data.to_folder_id").value(2))
        .andExpect(jsonPath("$.data.ticker").doesNotExist())
        .andExpect(jsonPath("$.data.analysis_summary").doesNotExist());
  }

  @Test
  @DisplayName("PUT /folders/items/move - to_folder_id 누락 시 400 INVALID_PARAM")
  void moveFavorite_missingTargetFolderId_400() throws Exception {
    mockMvc
        .perform(
            put("/folders/items/move")
                .header(HttpHeaders.AUTHORIZATION, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("item_id", 10))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_PARAM"));
  }

  @Test
  @DisplayName("PUT /folders/items/move - 대상 폴더 없으면 404 NOT_FOUND")
  void moveFavorite_targetNotFound_404() throws Exception {
    given(favoriteService.move(eq(USER_ID), eq(10L), any()))
        .willThrow(new DomainException(ErrorCode.NOT_FOUND, "이동할 폴더를 찾을 수 없습니다"));

    mockMvc
        .perform(
            put("/folders/items/move")
                .header(HttpHeaders.AUTHORIZATION, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("item_id", 10, "to_folder_id", 999))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("PUT /folders/items/move - 대상 폴더에 중복 종목이면 409 DUPLICATE")
  void moveFavorite_duplicate_409() throws Exception {
    given(favoriteService.move(eq(USER_ID), eq(10L), any()))
        .willThrow(new DomainException(ErrorCode.DUPLICATE, "대상 폴더에 이미 동일한 종목이 존재합니다"));

    mockMvc
        .perform(
            put("/folders/items/move")
                .header(HttpHeaders.AUTHORIZATION, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("item_id", 10, "to_folder_id", 2))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("DUPLICATE"));
  }

  @Test
  @DisplayName("PUT /folders/items/move - 토큰 없으면 401")
  void moveFavorite_noToken_401() throws Exception {
    mockMvc
        .perform(
            put("/folders/items/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("item_id", 10, "to_folder_id", 2))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("AUTH_INVALID"));
  }
}
