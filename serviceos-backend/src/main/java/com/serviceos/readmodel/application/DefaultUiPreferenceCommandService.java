package com.serviceos.readmodel.application;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.readmodel.api.UiPreferenceCommandService;
import com.serviceos.readmodel.api.UiPreferenceQueryService;
import com.serviceos.readmodel.api.UiPreferenceWrite;
import com.serviceos.readmodel.api.UiPreferencesDocument;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * 个人 UI Preference 写事务：按键 upsert 与乐观版本同事务提交。
 * 跨主体不可达；禁止键与白名单外键失败关闭。
 */
@Service
class DefaultUiPreferenceCommandService implements UiPreferenceCommandService {
    private final UiPreferenceRepository repository;
    private final UiPreferenceQueryService queries;
    private final Clock clock;

    DefaultUiPreferenceCommandService(
            UiPreferenceRepository repository,
            UiPreferenceQueryService queries,
            Clock clock
    ) {
        this.repository = repository;
        this.queries = queries;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UiPreferencesDocument put(
            CurrentPrincipal actor,
            String correlationId,
            String portal,
            Map<String, UiPreferenceWrite> preferences
    ) {
        UiPreferenceCatalog.requireAdminPortal(portal);
        if (preferences == null || preferences.isEmpty()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "preferences 不能为空");
        }
        Instant now = Instant.now(clock);
        for (Map.Entry<String, UiPreferenceWrite> entry : preferences.entrySet()) {
            String key = UiPreferenceCatalog.requireKey(entry.getKey());
            UiPreferenceWrite write = entry.getValue();
            if (write == null) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "偏好写入体不能为空：" + key);
            }
            UiPreferenceCatalog.validateWrite(key, write.schemaVersion(), write.value());
            upsert(actor, key, write, now);
        }
        return queries.get(actor, correlationId, UiPreferenceCatalog.PORTAL_ADMIN);
    }

    @Override
    @Transactional
    public void delete(CurrentPrincipal actor, String correlationId, String portal, String key) {
        UiPreferenceCatalog.requireAdminPortal(portal);
        String normalizedKey = UiPreferenceCatalog.requireKey(key);
        // 删除不存在的键视为幂等成功（恢复默认），避免客户端重试噪声。
        repository.deleteOwned(
                actor.tenantId(), actor.principalId(), UiPreferenceCatalog.PORTAL_ADMIN, normalizedKey);
    }

    private void upsert(CurrentPrincipal actor, String key, UiPreferenceWrite write, Instant now) {
        var existing = repository.findOwned(
                actor.tenantId(), actor.principalId(), UiPreferenceCatalog.PORTAL_ADMIN, key);
        if (existing.isEmpty()) {
            if (write.expectedVersion() != null && write.expectedVersion() > 0) {
                throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "偏好版本冲突，请刷新后重试");
            }
            UiPreferenceRepository.UiPreferenceRecord created = new UiPreferenceRepository.UiPreferenceRecord(
                    actor.tenantId(),
                    actor.principalId(),
                    UiPreferenceCatalog.PORTAL_ADMIN,
                    key,
                    UiPreferenceJson.write(write.value()),
                    write.schemaVersion(),
                    1L,
                    now,
                    now
            );
            try {
                repository.insert(created);
            } catch (DuplicateKeyException ex) {
                // 并发首次插入冲突：要求客户端带版本重试，避免静默覆盖。
                throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "偏好版本冲突，请刷新后重试");
            }
            return;
        }
        UiPreferenceRepository.UiPreferenceRecord current = existing.get();
        long expected = write.expectedVersion() != null
                ? write.expectedVersion()
                : current.aggregateVersion();
        UiPreferenceRepository.UiPreferenceRecord next = new UiPreferenceRepository.UiPreferenceRecord(
                current.tenantId(),
                current.principalId(),
                current.portal(),
                current.preferenceKey(),
                UiPreferenceJson.write(write.value()),
                write.schemaVersion(),
                current.aggregateVersion() + 1,
                current.createdAt(),
                now
        );
        if (!repository.update(next, expected)) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "偏好版本冲突，请刷新后重试");
        }
    }
}
