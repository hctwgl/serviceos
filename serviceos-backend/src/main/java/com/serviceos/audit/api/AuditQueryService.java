package com.serviceos.audit.api;

/**
 * 审计只读端口。调用方必须已完成租户与能力校验；本端口只按 tenant 约束查询。
 */
public interface AuditQueryService {
    AuditRecordPage listByTarget(
            String tenantId, String targetType, String targetId, int limit);

    /**
     * 按被拒主体（actor）列出 AUTHORIZATION_DENIED。
     *
     * <p>limit 范围 1～50；不含 digest / matched_grant_ids 等敏感细节。</p>
     */
    AuditRecordPage listAuthorizationDenialsByActor(
            String tenantId, String actorId, int limit);
}
