package com.serviceos.forms.api;

import java.util.UUID;

/**
 * 跨模块条件重解析使用的精确表单事实。完整值仅通过同进程只读端口返回，不进入事件或日志。
 */
public record FormSubmissionFacts(
        UUID submissionId,
        UUID taskId,
        UUID projectId,
        UUID formVersionId,
        String formKey,
        int submissionVersion,
        String valuesJson,
        String contentDigest
) {
}
