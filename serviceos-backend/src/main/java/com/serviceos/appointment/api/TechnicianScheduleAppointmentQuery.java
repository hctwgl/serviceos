package com.serviceos.appointment.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 按任务 fan-in 预约日程（仅非敏感字段）。调用方负责 Technician Portal 鉴权。
 */
public interface TechnicianScheduleAppointmentQuery {
    List<TechnicianScheduleAppointmentView> listForTasks(String tenantId, Collection<UUID> taskIds);
}
