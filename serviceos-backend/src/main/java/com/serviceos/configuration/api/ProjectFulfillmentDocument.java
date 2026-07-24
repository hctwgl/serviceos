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
        List<ProjectFulfillmentStageDraft> stages,
        List<ProjectFulfillmentPhaseDraft> phases,
        List<ProjectFulfillmentNodeDraft> nodes,
        List<ProjectFulfillmentTransitionDraft> transitions
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
        phases = List.copyOf(phases == null ? List.of() : phases);
        nodes = List.copyOf(nodes == null ? List.of() : nodes);
        transitions = List.copyOf(transitions == null ? List.of() : transitions);
    }

    public ProjectFulfillmentDocument(
            String schemaVersion,
            String orderTypeName,
            List<String> supportedClientKinds,
            List<ProjectFulfillmentStageDraft> stages
    ) {
        this(schemaVersion, orderTypeName, ProjectFulfillmentMatchRule.unrestricted(),
                supportedClientKinds, stages, List.of(), List.of(), List.of());
    }

    /** 兼容现有 Profile 构造；新设计器应显式传入 Phase、Node 与 Transition。 */
    public ProjectFulfillmentDocument(
            String schemaVersion,
            String orderTypeName,
            ProjectFulfillmentMatchRule matchRule,
            List<String> supportedClientKinds,
            List<ProjectFulfillmentStageDraft> stages
    ) {
        this(schemaVersion, orderTypeName, matchRule, supportedClientKinds, stages,
                List.of(), List.of(), List.of());
    }
}
