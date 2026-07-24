package com.serviceos.configuration.api;

import java.util.List;

/**
 * 履约草稿结构化文档。
 *
 * <p>产品 UI 必须读写本对象；持久化仍序列化为 JSON，但不形成第二套前端解释。</p>
 */
public record ProjectFulfillmentDocument(
        String schemaVersion,
        String orderTypeName,
        ProjectFulfillmentMatchRule matchRule,
        List<String> supportedClientKinds,
        List<ProjectFulfillmentStageDraft> stages
) {
    public ProjectFulfillmentDocument {
        if (schemaVersion == null || schemaVersion.isBlank()) {
            schemaVersion = "1.0.0";
        }
        matchRule = matchRule == null
                ? ProjectFulfillmentMatchRule.unrestricted()
                : matchRule;
        supportedClientKinds = List.copyOf(supportedClientKinds == null ? List.of() : supportedClientKinds);
        stages = List.copyOf(stages == null ? List.of() : stages);
    }

    public ProjectFulfillmentDocument(
            String schemaVersion,
            String orderTypeName,
            List<String> supportedClientKinds,
            List<ProjectFulfillmentStageDraft> stages
    ) {
        this(schemaVersion, orderTypeName, ProjectFulfillmentMatchRule.unrestricted(),
                supportedClientKinds, stages);
    }
}
