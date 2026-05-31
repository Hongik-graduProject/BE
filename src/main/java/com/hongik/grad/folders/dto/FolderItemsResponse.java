package com.hongik.grad.folders.dto;

import java.util.List;

/** §3-6 GET /folders/{folder_id}/items response: folder header + enriched items. */
public record FolderItemsResponse(FolderBriefResponse folder, List<FavoriteItemResponse> items) {}
