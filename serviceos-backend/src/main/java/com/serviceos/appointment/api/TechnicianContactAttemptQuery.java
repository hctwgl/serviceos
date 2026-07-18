package com.serviceos.appointment.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 按任务 fan-in 联系历史的非敏感摘要。调用方负责 Technician Portal 上下文、能力与当前责任校验。
 */
public interface TechnicianContactAttemptQuery {
    List<TechnicianContactAttemptView> listForTasks(String tenantId, Collection<UUID> taskIds);
}
