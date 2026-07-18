package com.serviceos.fieldwork.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** 按任务 fan-in Technician Visit 安全历史；调用方负责 Portal 上下文和当前责任。 */
public interface TechnicianVisitHistoryQuery {
    List<TechnicianVisitHistoryView> listForTasks(String tenantId, Collection<UUID> taskIds);
}
