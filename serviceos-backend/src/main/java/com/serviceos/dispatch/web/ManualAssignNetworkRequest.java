package com.serviceos.dispatch.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin 仅派网点 HTTP 请求体（不强制师傅）。 */
record ManualAssignNetworkRequest(
        @NotBlank @Size(max = 128) String networkAssigneeId,
        @NotBlank @Size(max = 100) String businessType
) {
}
