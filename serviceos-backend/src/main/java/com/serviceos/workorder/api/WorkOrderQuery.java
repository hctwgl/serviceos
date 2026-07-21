package com.serviceos.workorder.api;

import java.util.UUID;

/**
 * 工单目录查询；非法筛选或游标必须失败，不能退化为无筛选查询。
 *
 * <p>M437：可选 {@code provinceCode}/{@code cityCode}/{@code districtCode} 精确匹配（AND）；
 * 均写入 cursor filterDigest。</p>
 *
 * <p>M438：可选 {@code currentStageCode} 与目录列同口径精确匹配，写入 cursor filterDigest。</p>
 *
 * <p>M440：可选 {@code currentNetworkId} 与目录网点列同口径（ACTIVE NETWORK assignee）精确匹配，
 * 写入 cursor filterDigest。</p>
 */
public record WorkOrderQuery(
        String clientCode,
        UUID projectId,
        String status,
        String externalOrderCode,
        String provinceCode,
        String cityCode,
        String districtCode,
        String currentStageCode,
        UUID currentNetworkId,
        String cursor,
        int limit
) {
    /** 无区域/阶段/网点筛选的常用构造。 */
    public WorkOrderQuery(String clientCode, UUID projectId, String status, String cursor, int limit) {
        this(clientCode, projectId, status, null, null, null, null, null, null, cursor, limit);
    }

    /** 含 externalOrderCode、无区域/阶段/网点筛选（受控搜索等）。 */
    public WorkOrderQuery(
            String clientCode, UUID projectId, String status, String externalOrderCode,
            String cursor, int limit
    ) {
        this(clientCode, projectId, status, externalOrderCode, null, null, null, null, null, cursor, limit);
    }
}
