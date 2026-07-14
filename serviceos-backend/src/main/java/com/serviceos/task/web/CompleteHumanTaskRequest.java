package com.serviceos.task.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 完成人工任务的外部载荷；actor/tenant/version 不允许由请求正文覆盖。 */
record CompleteHumanTaskRequest(
        @NotBlank @Size(max = 500) String resultRef,
        @NotBlank @Pattern(regexp = "^[0-9a-f]{64}$") String resultDigest
) {
}
