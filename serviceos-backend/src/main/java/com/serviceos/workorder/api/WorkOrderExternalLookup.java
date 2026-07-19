package com.serviceos.workorder.api;

import java.util.Optional;

/**
 * 外部订单定位端口。
 *
 * <p>供 integration 模块在入站取消/更新管道中解析工单身份与乐观锁版本；
 * 不授予 HTTP 查询能力，也不返回客户 PII。</p>
 */
public interface WorkOrderExternalLookup {
    Optional<ExternalWorkOrderPointer> findByExternalOrder(
            String tenantId,
            String clientCode,
            String externalOrderCode
    );
}
