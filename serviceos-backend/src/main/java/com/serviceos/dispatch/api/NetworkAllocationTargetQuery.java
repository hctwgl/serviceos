package com.serviceos.dispatch.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 查询项目内网点当前有效签约比例目标（committedShare，0～1）。
 */
public interface NetworkAllocationTargetQuery {
    /**
     * @return networkId → committedShare；无目标时返回空 Map
     */
    Map<String, Double> listCommittedShares(
            String tenantId,
            UUID projectId,
            String brandCode,
            String businessType,
            Instant asOf
    );
}
