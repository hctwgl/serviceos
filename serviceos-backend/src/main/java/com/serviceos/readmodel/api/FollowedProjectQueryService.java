package com.serviceos.readmodel.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.UUID;

/** Admin 个人关注项目只读用例。 */
public interface FollowedProjectQueryService {
    FollowedProjectPage list(CurrentPrincipal actor, String correlationId, String portal, Integer limit);

    boolean isFollowed(CurrentPrincipal actor, String correlationId, String portal, UUID projectId);
}
