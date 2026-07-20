package com.serviceos.configuration.api;

import java.util.List;

/**
 * 产品化运行说明书。由服务端从编译 Manifest/草稿单一解释生成，前端只渲染。
 */
public record ProjectFulfillmentRunbook(
        String profileName,
        String serviceProductCode,
        String serviceProductLabel,
        String orderTypeName,
        String versionLabel,
        int stageCount,
        List<ProjectFulfillmentRunbookStage> stages,
        String clientSupportSummary,
        String impactSummary
) {
    public ProjectFulfillmentRunbook {
        stages = List.copyOf(stages == null ? List.of() : stages);
    }
}
