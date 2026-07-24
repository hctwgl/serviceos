package com.serviceos.dispatch.api;

import java.util.Optional;
import java.util.UUID;

/**
 * 下游履约能力按当前 Task 读取整张工单的 ACTIVE ServiceAssignment。
 *
 * <p>责任在首个需要履约的 Task 上建立，但跨后续阶段持续有效；返回的 taskId 始终是调用方
 * 当前 Task，避免各 Portal 直接理解 Dispatch 的历史落点。</p>
 */
public interface ActiveServiceResponsibilityService {
    Optional<ActiveServiceResponsibility> find(String tenantId, UUID taskId);
}
