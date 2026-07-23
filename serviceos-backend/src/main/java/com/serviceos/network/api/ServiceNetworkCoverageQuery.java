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
    /**
     * 按网点查询当前全部有效覆盖，用于后台资源目录和运维检查。
     * 返回结果仍只包含服务端确认有效的覆盖，不允许调用方自行补默认区域。
     */
    List<ServiceNetworkCoverageView> listActiveCoverageByNetworks(
            String tenantId,
            Collection<String> serviceNetworkIds,
            Instant asOf
    );

    List<ServiceNetworkCoverageView> listActiveCoverage(
            String tenantId,
            Collection<String> serviceNetworkIds,
            String brandCode,
            String businessType,
            Instant asOf
    );
}
