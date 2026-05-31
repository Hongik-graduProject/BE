package com.hongik.grad.folders.dto;

import java.util.List;

/** Wraps the folder list under a {@code folders} key per spec §3-1. */
public record FolderListWrapper(List<FolderListResponse> folders) {}
