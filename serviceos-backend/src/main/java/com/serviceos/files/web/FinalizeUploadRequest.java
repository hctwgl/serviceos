package com.serviceos.files.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record FinalizeUploadRequest(
        @NotBlank @Size(min = 64, max = 64) String actualSha256,
        @NotBlank @Size(max = 160) String finalizeCommandId
) {
}
