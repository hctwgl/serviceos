package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.Map;

/**
 * 当前主体在指定 Portal 下的个人 UI 偏好文档。
 */
public record UiPreferencesDocument(
        String portal,
        Map<String, UiPreferenceEntry> preferences,
        Instant asOf
) {
}
