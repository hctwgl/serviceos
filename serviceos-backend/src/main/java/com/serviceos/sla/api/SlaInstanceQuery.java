package com.serviceos.sla.api;

import java.util.UUID;

/** SLA 工作台查询；projectId 为空时由服务端实时解析主体的授权项目集合。 */
public record SlaInstanceQuery(UUID projectId, String status, String cursor, int limit) {
    public SlaInstanceQuery {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
    }
}
