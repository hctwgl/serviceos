package com.serviceos.files.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record AuthorizeDownloadRequest(@NotBlank @Size(max = 500) String purpose) {
}
