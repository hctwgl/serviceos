package com.serviceos.project.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

record CreateProjectRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]*") String code,
        @NotBlank @Size(max = 128) String clientId,
        @NotBlank @Size(max = 200) String name,
        @NotNull LocalDate startsOn,
        LocalDate endsOn,
        @Size(max = 100) List<@NotBlank @Size(max = 128) String> regionCodes
) {
}
