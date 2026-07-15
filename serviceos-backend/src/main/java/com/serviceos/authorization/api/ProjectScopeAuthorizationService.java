package com.serviceos.authorization.api;

import com.serviceos.identity.api.CurrentPrincipal;

/** 解析主体对指定能力的实时项目集合；拒绝结果必须审计并失败关闭。 */
public interface ProjectScopeAuthorizationService {
    AuthorizedProjectScope require(
            CurrentPrincipal principal,
            String capability,
            String resourceType,
            String correlationId
    );
}
