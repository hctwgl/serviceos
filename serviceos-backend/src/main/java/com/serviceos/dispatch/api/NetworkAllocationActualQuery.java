package com.serviceos.dispatch.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 查询月度 NETWORK 派单实际量（ORDER_COUNT 口径）。
 *
 * <p>统计当月已创建的 NETWORK ServiceAssignment（ACTIVE/ENDED），按项目/品牌/业务过滤。</p>
 */
public interface NetworkAllocationActualQuery {
    /**
     * @return networkId → 当月派单次数；无记录时返回空 Map
     */
    Map<String, Long> countMonthlyNetworkAssignments(
            String tenantId,
            UUID projectId,
            String brandCode,
            String businessType,
            Instant asOf
    );
}
