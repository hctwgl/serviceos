package com.serviceos.identity.api;

import java.util.List;
import java.util.UUID;

public interface SecurityPrincipalQueryService {
    SecurityPrincipalPage list(
            CurrentPrincipal actor, String correlationId, String query, String status, String cursor, int limit);

    SecurityPrincipalDetail get(CurrentPrincipal actor, String correlationId, UUID principalId);

    List<IdentityLinkView> identities(CurrentPrincipal actor, String correlationId, UUID principalId);

    /** 最近成功登录；需要 identity.read。不含 subject/密码。 */
    PrincipalLoginEventPage recentLogins(
            CurrentPrincipal actor, String correlationId, UUID principalId, Integer limit);

    /** 主体变更时间线（生命周期 + 审计 + 登录）；需要 identity.read。 */
    PrincipalChangeTimelinePage changeTimeline(
            CurrentPrincipal actor, String correlationId, UUID principalId, Integer limit);

    /**
     * 主体授权拒绝安全活动流；硬门禁 identity.read，soft-gate authorization.read。
     * 不并入 change-timeline。
     */
    PrincipalAuthorizationDenialPage authorizationDenials(
            CurrentPrincipal actor, String correlationId, UUID principalId, Integer limit);
}
