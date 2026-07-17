package com.serviceos.readmodel.application;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.readmodel.api.UiPreferenceEntry;
import com.serviceos.readmodel.api.UiPreferenceQueryService;
import com.serviceos.readmodel.api.UiPreferencesDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
class DefaultUiPreferenceQueryService implements UiPreferenceQueryService {
    private final UiPreferenceRepository repository;
    private final Clock clock;

    DefaultUiPreferenceQueryService(UiPreferenceRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public UiPreferencesDocument get(CurrentPrincipal actor, String correlationId, String portal) {
        UiPreferenceCatalog.requireAdminPortal(portal);
        // 仅返回本人租户内偏好；不授予任何页面 capability。
        Map<String, UiPreferenceEntry> preferences = new LinkedHashMap<>();
        for (UiPreferenceRepository.UiPreferenceRecord record : repository.listByOwner(
                actor.tenantId(), actor.principalId(), UiPreferenceCatalog.PORTAL_ADMIN)) {
            preferences.put(record.preferenceKey(), UiPreferenceJson.toEntry(record));
        }
        return new UiPreferencesDocument(
                UiPreferenceCatalog.PORTAL_ADMIN,
                preferences,
                Instant.now(clock));
    }
}
