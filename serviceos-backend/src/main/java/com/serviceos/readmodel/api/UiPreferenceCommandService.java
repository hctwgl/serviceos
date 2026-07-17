package com.serviceos.readmodel.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.Map;

/**
 * 个人 UI Preference 写入。任意已认证主体仅可更新自己的偏好；
 * 跨主体不可达，避免泄露他人偏好。
 */
public interface UiPreferenceCommandService {
    UiPreferencesDocument put(
            CurrentPrincipal actor,
            String correlationId,
            String portal,
            Map<String, UiPreferenceWrite> preferences
    );

    void delete(CurrentPrincipal actor, String correlationId, String portal, String key);
}
