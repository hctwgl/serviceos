package com.serviceos.project.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 两类集合均显式必填，避免缺字段被解释为保留或清空。 */
record ReviseProjectScopeRelationsRequest(
        @NotNull @Size(max = 100) List<@NotBlank @Size(max = 128) String> regionCodes,
        @NotNull @Size(max = 100) List<@NotBlank @Size(max = 128) String> networkIds,
        @NotBlank @Size(max = 500) String reason
) {
}
