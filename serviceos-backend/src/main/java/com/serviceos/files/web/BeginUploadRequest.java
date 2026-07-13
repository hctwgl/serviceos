package com.serviceos.files.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

record BeginUploadRequest(
        @NotBlank @Size(max = 80) String businessContextType,
        @NotBlank @Size(max = 160) String businessContextId,
        @NotBlank @Size(max = 255) String originalFileName,
        @NotBlank @Size(max = 255) String declaredMimeType,
        @Positive long expectedSize,
        @NotBlank @Size(min = 64, max = 64) String expectedSha256
) {
}
