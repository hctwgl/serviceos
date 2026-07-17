package com.serviceos.dispatch.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin 人工初派 HTTP 请求体。 */
record ManualAssignServiceAssignmentRequest(
        @NotBlank @Size(max = 128) String networkAssigneeId,
        @NotBlank @Size(max = 128) String technicianAssigneeId,
        @NotBlank @Size(max = 100) String businessType
) {
}
