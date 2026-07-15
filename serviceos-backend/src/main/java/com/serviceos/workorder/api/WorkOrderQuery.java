package com.serviceos.workorder.api;

import java.util.UUID;

/** 工单目录查询；非法筛选或游标必须失败，不能退化为无筛选查询。 */
public record WorkOrderQuery(String clientCode, UUID projectId, String status, String cursor, int limit) {
}
