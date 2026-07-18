package com.serviceos.configuration.web;

import java.util.List;
import java.util.UUID;

final class DependencyReportHttpModels {
    private DependencyReportHttpModels() {
    }

    record DependencyReportResponse(
            String assetType,
            String assetKey,
            UUID draftId,
            UUID bundleId,
            boolean complete,
            List<DependencyItemResponse> dependencies
    ) {
    }

    record DependencyItemResponse(
            String refField,
            String refValue,
            String sourceNodeId,
            String expectedAssetType,
            String status,
            UUID satisfiedVersionId,
            String detail
    ) {
    }
}
