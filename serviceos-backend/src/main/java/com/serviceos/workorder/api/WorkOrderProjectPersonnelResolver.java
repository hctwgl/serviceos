package com.serviceos.workorder.api;

import java.time.Instant;
import java.util.UUID;

/**
 * 工单创建所需的项目岗位人员解析 SPI。
 *
 * <p>工单模块拥有所需契约，项目模块提供实现。调用发生在工单创建事务中，因此实现不得产生
 * 外部网络副作用，也不得执行页面授权；入口已经完成租户、项目和请求合法性校验。</p>
 */
public interface WorkOrderProjectPersonnelResolver {
    WorkOrderProjectPersonnelResolution resolve(
            String tenantId, UUID projectId, String regionCode, Instant matchedAt);
}
