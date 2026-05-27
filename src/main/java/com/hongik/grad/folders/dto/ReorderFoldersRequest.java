package com.hongik.grad.folders.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ReorderFoldersRequest(@NotEmpty List<Long> folderIds) {}
