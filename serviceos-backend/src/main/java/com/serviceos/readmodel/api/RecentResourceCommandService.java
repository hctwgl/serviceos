package com.serviceos.readmodel.api;

import com.serviceos.identity.api.CurrentPrincipal;

/**
 * 个人最近访问写入。任意已认证主体仅可 touch 自己的列表；不要求新 capability。
 */
public interface RecentResourceCommandService {
    RecentResourceItem touch(
            CurrentPrincipal actor,
            String correlationId,
            String portal,
            RecentResourceTouch touch
    );
}
