package com.serviceos.sla.api;

import java.util.Optional;
import java.util.UUID;

/** SLA 查询与漏触发对账入口；调度器只负责唤醒，数据库里程碑仍是事实源。 */
public interface SlaClockService {
    Optional<SlaInstanceView> findByTask(String tenantId, UUID taskId);

    boolean detectNextBreach();
}
