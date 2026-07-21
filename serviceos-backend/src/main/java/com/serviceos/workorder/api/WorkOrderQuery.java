package com.serviceos.workorder.api;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 工单目录查询；非法筛选或游标必须失败，不能退化为无筛选查询。
 *
 * <p>M437：可选 {@code provinceCode}/{@code cityCode}/{@code districtCode} 精确匹配（AND）；
 * 均写入 cursor filterDigest。</p>
 *
 * <p>M438：可选 {@code currentStageCode} 与目录列同口径精确匹配，写入 cursor filterDigest。</p>
 *
 * <p>M446：可选 {@code currentTaskStatus} 与目录列同口径（最早 ACTIVE 任务 status）精确匹配，
 * 写入 cursor filterDigest。</p>
 *
 * <p>M440：可选 {@code currentNetworkId} 与目录网点列同口径（ACTIVE NETWORK assignee）精确匹配，
 * 写入 cursor filterDigest。</p>
 *
 * <p>M441：可选 {@code currentTechnicianId} 与目录师傅列同口径（ACTIVE TECHNICIAN assignee）精确匹配，
 * 写入 cursor filterDigest。</p>
 *
 * <p>M442：可选 {@code slaRisk}（{@code OPEN}/{@code BREACHED}）与目录 SLA 列同口径精确匹配，
 * 写入 cursor filterDigest；仅在具备 PROJECT {@code sla.read} 的项目范围内解析。</p>
 *
 * <p>M445：{@code slaRisk=NEAR} 为 RUNNING 且 deadline 落在 now 后 30 分钟内的即将超时窗口。</p>
 *
 * <p>M443：可选 {@code receivedFrom}/{@code receivedTo}（Asia/Shanghai 自然日闭区间）按
 * {@code receivedAt} 精确匹配，写入 cursor filterDigest。</p>
 *
 * <p>M447：可选 {@code reviewCorrectionStatus}（{@code REVIEW_OPEN}/{@code CORRECTION_ACTIVE}）
 * 按审核/整改运营桶筛选，写入 cursor filterDigest；仅在具备 PROJECT {@code evidence.read} 的范围内解析。</p>
 *
 * <p>M448：可选 {@code q} 服务端关键词（工单编号/客户名/手机后四位/地址）；完整手机号失败关闭；
 * 写入 cursor filterDigest。精确 {@code externalOrderCode} 仍供受控搜索等内部调用。</p>
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
        String currentTaskStatus,
        UUID currentNetworkId,
        UUID currentTechnicianId,
        String slaRisk,
        LocalDate receivedFrom,
        LocalDate receivedTo,
        String reviewCorrectionStatus,
        String q,
        String cursor,
        int limit
) {
    /** 无区域/阶段/任务状态/网点/师傅/SLA/创建日/审核整改/关键词筛选的常用构造。 */
    public WorkOrderQuery(String clientCode, UUID projectId, String status, String cursor, int limit) {
        this(clientCode, projectId, status, null, null, null, null, null, null, null, null, null, null, null, null, null, cursor, limit);
    }

    /** 含 externalOrderCode、无区域等扩展筛选（受控搜索等）。 */
    public WorkOrderQuery(
            String clientCode, UUID projectId, String status, String externalOrderCode,
            String cursor, int limit
    ) {
        this(clientCode, projectId, status, externalOrderCode, null, null, null, null, null, null, null, null, null, null, null, null, cursor, limit);
    }
}
