package com.serviceos.audit.api;

/**
 * 审计只读端口。调用方必须已完成租户与能力校验；本端口只按 tenant 约束查询。
 */
public interface AuditQueryService {
    AuditRecordPage listByTarget(
            String tenantId, String targetType, String targetId, int limit);
}
