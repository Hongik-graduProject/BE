package com.hongik.grad.users.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProfileResponse(
    UUID userId, String nickname, Long defaultFolderId, OffsetDateTime createdAt) {}
