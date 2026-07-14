package com.serviceos.dispatch.application;

import com.serviceos.dispatch.api.ActiveServiceResponsibility;

import java.util.Optional;
import java.util.UUID;

/** Dispatch 内部持久化端口。 */
public interface ActiveServiceResponsibilityRepository {
    Optional<ActiveServiceResponsibility> find(String tenantId, UUID taskId);
}
