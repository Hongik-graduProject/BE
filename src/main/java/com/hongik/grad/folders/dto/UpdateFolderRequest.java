package com.hongik.grad.folders.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateFolderRequest(
    @NotBlank @Size(max = 30) String name,
    @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color must be a 6-digit hex code")
        String color) {}
