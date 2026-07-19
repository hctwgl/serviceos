package com.serviceos.forms.api;

import java.util.List;
import java.util.UUID;

/** Task 对其锁定 ConfigurationBundle 中精确 FormVersion 的不可变解析结果。 */
public record TaskFormDefinition(
        UUID taskId,
        UUID formVersionId,
        String formKey,
        String semanticVersion,
        String schemaVersion,
        String definitionJson,
        String contentDigest,
        List<String> supportedClientKinds
) {
    public TaskFormDefinition {
        supportedClientKinds = supportedClientKinds == null
                ? List.of() : List.copyOf(supportedClientKinds);
    }

    /** 未声明定向目标的兼容构造。 */
    public TaskFormDefinition(
            UUID taskId,
            UUID formVersionId,
            String formKey,
            String semanticVersion,
            String schemaVersion,
            String definitionJson,
            String contentDigest
    ) {
        this(taskId, formVersionId, formKey, semanticVersion, schemaVersion,
                definitionJson, contentDigest, List.of());
    }
}
