package com.serviceos.evidence.application;

import java.util.List;

/** 单个已发布 EvidenceTemplate 的解析结果，保留已创建要求及全部条件决策。 */
public record ResolvedEvidenceTemplate(
        List<ResolvedEvidenceRequirement> requirements,
        List<ResolvedEvidenceCondition> conditions
) {
    public ResolvedEvidenceTemplate {
        requirements = List.copyOf(requirements);
        conditions = List.copyOf(conditions);
    }

    public static ResolvedEvidenceTemplate empty() {
        return new ResolvedEvidenceTemplate(List.of(), List.of());
    }
}
