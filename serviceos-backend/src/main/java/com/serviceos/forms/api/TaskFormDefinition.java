package com.serviceos.forms.api;

import java.util.UUID;

/** Task 对其锁定 ConfigurationBundle 中精确 FormVersion 的不可变解析结果。 */
public record TaskFormDefinition(
        UUID taskId,
        UUID formVersionId,
        String formKey,
        String semanticVersion,
        String schemaVersion,
        String definitionJson,
        String contentDigest
) {
}
