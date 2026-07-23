package com.serviceos.workorder.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 工单创建时解析项目岗位人员的内部结果。
 *
 * <p>类型由工单模块定义，避免工单命令反向依赖项目模块。项目模块负责提供解析实现，
 * 工单模块只负责在自己的事务中固化不可变责任快照。</p>
 */
public record WorkOrderProjectPersonnelResolution(
        UUID projectId,
        String requestedRegionCode,
        List<Item> items,
        Instant matchedAt
) {
    public WorkOrderProjectPersonnelResolution {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** 每个固定岗位都返回一项；无法匹配时通过 MISSING 明确表达，不允许默认人员兜底。 */
    public record Item(
            String positionCode,
            UUID assignmentId,
            UUID principalId,
            String displayName,
            String matchedRegionCode,
            String matchedRegionName,
            boolean inherited,
            String status
    ) {
    }
}
