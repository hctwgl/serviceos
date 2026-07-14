package com.serviceos.task.web;

import com.serviceos.task.api.AssignmentSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 已解析候选快照的 HTTP 载荷；候选 USER ID 不携带权限声明。 */
record AssignTaskCandidatesRequest(
        @NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 128) String> candidatePrincipalIds,
        @NotNull AssignmentSourceType sourceType,
        @NotBlank @Size(max = 160) String sourceId
) {
}
