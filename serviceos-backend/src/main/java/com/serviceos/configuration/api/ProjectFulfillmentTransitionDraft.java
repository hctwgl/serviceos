package com.serviceos.configuration.api;

import java.util.Map;

/** 串行流程中的互斥流转；一次节点完成只能命中一条。 */
public record ProjectFulfillmentTransitionDraft(
        String transitionId,
        String fromNodeId,
        String toNodeId,
        String resultCode,
        String branchName,
        boolean defaultBranch,
        Map<String, Object> condition
) {
    public ProjectFulfillmentTransitionDraft {
        condition = condition == null ? Map.of() : Map.copyOf(condition);
    }
}
