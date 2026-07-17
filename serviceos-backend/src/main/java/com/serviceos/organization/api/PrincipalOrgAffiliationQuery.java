package com.serviceos.organization.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 主体有效企业任职只读端口，供 Portal 上下文计算；调用方必须只查询当前主体自身。
 */
public interface PrincipalOrgAffiliationQuery {
    List<OrgMembershipView> listActiveMemberships(String tenantId, UUID principalId, Instant at);
}
