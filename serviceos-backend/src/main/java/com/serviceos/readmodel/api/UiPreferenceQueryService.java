package com.serviceos.readmodel.api;

import com.serviceos.identity.api.CurrentPrincipal;

/**
 * 个人 UI Preference 查询。仅返回当前主体在指定 Portal 下的偏好。
 */
public interface UiPreferenceQueryService {
    UiPreferencesDocument get(CurrentPrincipal actor, String correlationId, String portal);
}
