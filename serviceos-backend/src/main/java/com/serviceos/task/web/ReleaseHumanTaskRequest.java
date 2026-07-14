package com.serviceos.task.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 释放原因使用稳定代码，展示文案由版本化原因库负责。 */
record ReleaseHumanTaskRequest(
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,99}$") String reasonCode
) {
}
