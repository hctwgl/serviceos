package com.serviceos.network.api;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * 查询有效网点覆盖（品牌/业务/行政区）。
 *
 * <p>仅返回 ACTIVE 且时间窗覆盖 {@code asOf} 的行；调用方再按工单省市区精确匹配。</p>
 */
public interface ServiceNetworkCoverageQuery {
    List<ServiceNetworkCoverageView> listActiveCoverage(
            String tenantId,
            Collection<String> serviceNetworkIds,
            String brandCode,
            String businessType,
            Instant asOf
    );
}
