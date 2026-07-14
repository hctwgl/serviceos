package com.serviceos.dispatch.api;

import java.util.Optional;
import java.util.UUID;

/** 下游履约能力读取当前 ACTIVE ServiceAssignment 的公开只读边界。 */
public interface ActiveServiceResponsibilityService {
    Optional<ActiveServiceResponsibility> find(String tenantId, UUID taskId);
}
