package com.serviceos.network.api;

import java.util.UUID;

/**
 * 网点 ServiceCoverage 只读视图（M337 DISPATCH 地图 scope）。
 */
public record ServiceNetworkCoverageView(
        UUID coverageId,
        UUID serviceNetworkId,
        String brandCode,
        String businessType,
        String regionCode
) {
}
