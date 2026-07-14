package com.serviceos.dispatch.api;

import java.util.UUID;

/** 某 Task 当前已激活的网点与师傅服务责任快照。 */
public record ActiveServiceResponsibility(
        UUID taskId,
        String networkId,
        String technicianId
) {
}
