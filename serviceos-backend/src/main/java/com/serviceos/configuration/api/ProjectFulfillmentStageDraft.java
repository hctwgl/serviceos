package com.serviceos.configuration.api;

import java.util.List;
import java.util.Map;

/** 履约草稿中的单个阶段（产品可编辑字段）。 */
public record ProjectFulfillmentStageDraft(
        String stageCode,
        String stageName,
        int sequence,
        String stageType,
        String taskType,
        String ownerType,
        String description,
        List<String> formRefs,
        List<String> evidenceRefs,
        List<Map<String, Object>> actions,
        List<Map<String, Object>> transitions,
        List<Map<String, Object>> exceptionPaths,
        String slaRef,
        boolean terminal
) {
    public ProjectFulfillmentStageDraft {
        formRefs = List.copyOf(formRefs == null ? List.of() : formRefs);
        evidenceRefs = List.copyOf(evidenceRefs == null ? List.of() : evidenceRefs);
        actions = List.copyOf(actions == null ? List.of() : actions);
        transitions = List.copyOf(transitions == null ? List.of() : transitions);
        exceptionPaths = List.copyOf(exceptionPaths == null ? List.of() : exceptionPaths);
    }
}
