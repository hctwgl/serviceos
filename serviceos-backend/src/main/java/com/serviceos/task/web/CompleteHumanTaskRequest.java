package com.serviceos.task.web;

import com.serviceos.task.api.InputVersionRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 完成人工任务的外部载荷；actor/tenant/version 不允许由请求正文覆盖。 */
record CompleteHumanTaskRequest(
        @NotBlank @Size(max = 500) String resultRef,
        @NotBlank @Pattern(regexp = "^[0-9a-f]{64}$") String resultDigest,
        @Valid @Size(max = 8) List<InputVersionRefRequest> inputVersionRefs
) {
    record InputVersionRefRequest(
            @NotBlank @Size(max = 40) String kind,
            @NotBlank @Size(max = 500) String ref,
            @NotBlank @Pattern(regexp = "^[0-9a-f]{64}$") String digest
    ) {
        InputVersionRef toApi() {
            return new InputVersionRef(kind, ref, digest);
        }
    }
}
