package com.serviceos.readmodel.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.UUID;

/** Admin 个人关注项目写用例。 */
public interface FollowedProjectCommandService {
    FollowedProjectItem follow(
            CurrentPrincipal actor, String correlationId, String portal, UUID projectId, String displayRefHint);

    void unfollow(CurrentPrincipal actor, String correlationId, String portal, UUID projectId);
}
