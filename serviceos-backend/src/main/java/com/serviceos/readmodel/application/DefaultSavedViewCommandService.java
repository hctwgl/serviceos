package com.serviceos.readmodel.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.authorization.api.PrincipalActiveRoleQuery;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.readmodel.api.SavedView;
import com.serviceos.readmodel.api.SavedViewCommandService;
import com.serviceos.readmodel.api.SavedViewFilterAst;
import com.serviceos.readmodel.api.SavedViewSortSpec;
import com.serviceos.readmodel.api.SavedViewVisibility;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * SavedView 写事务：聚合、可见性与审计同事务提交。
 * 跨主体/跨租户按 RESOURCE_NOT_FOUND 失败关闭；分享 ROLE/TENANT 需 preference.shareSavedView。
 */
@Service
class DefaultSavedViewCommandService implements SavedViewCommandService {
    private static final String PORTAL = "ADMIN";
    static final String SHARE_CAPABILITY = "preference.shareSavedView";

    private final SavedViewRepository repository;
    private final AuthorizationService authorization;
    private final PrincipalActiveRoleQuery activeRoles;
    private final AuditAppender audit;
    private final Clock clock;

    DefaultSavedViewCommandService(
            SavedViewRepository repository,
            AuthorizationService authorization,
            PrincipalActiveRoleQuery activeRoles,
            AuditAppender audit,
            Clock clock
    ) {
        this.repository = repository;
        this.authorization = authorization;
        this.activeRoles = activeRoles;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SavedView create(
            CurrentPrincipal actor,
            String correlationId,
            String pageId,
            String name,
            int schemaVersion,
            SavedViewFilterAst filter,
            SavedViewSortSpec sort,
            List<String> columns,
            boolean isDefault
    ) {
        String normalizedPage = requireText(pageId, "pageId");
        String normalizedName = requireText(name, "name");
        if (normalizedName.length() > 120) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "name 过长");
        }
        SavedViewFilterCatalog.validateCreate(normalizedPage, schemaVersion, filter, sort);
        Instant now = Instant.now(clock);
        if (isDefault) {
            repository.clearDefault(actor.tenantId(), actor.principalId(), PORTAL, normalizedPage);
        }
        UUID id = UUID.randomUUID();
        SavedViewRepository.SavedViewRecord record = new SavedViewRepository.SavedViewRecord(
                id,
                actor.tenantId(),
                actor.principalId(),
                PORTAL,
                normalizedPage,
                normalizedName,
                SavedViewVisibility.PRIVATE,
                null,
                schemaVersion,
                SavedViewJson.write(filter),
                SavedViewJson.write(sort),
                SavedViewJson.write(columns),
                isDefault,
                1L,
                now,
                now
        );
        try {
            repository.insert(record);
        } catch (DuplicateKeyException ex) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "同页面下保存视图名称已存在");
        }
        return SavedViewJson.toView(record);
    }

    @Override
    @Transactional
    public SavedView update(
            CurrentPrincipal actor,
            String correlationId,
            UUID savedViewId,
            long expectedVersion,
            String name,
            int schemaVersion,
            SavedViewFilterAst filter,
            SavedViewSortSpec sort,
            List<String> columns,
            boolean isDefault
    ) {
        if (savedViewId == null) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "savedViewId 不能为空");
        }
        if (expectedVersion < 1) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "expectedVersion 无效");
        }
        SavedViewRepository.SavedViewRecord existing = repository
                .findOwned(actor.tenantId(), actor.principalId(), savedViewId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "保存视图不存在"));
        String normalizedName = requireText(name, "name");
        if (normalizedName.length() > 120) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "name 过长");
        }
        // 目录不兼容时拒绝更新，强制客户端按新 schema 重建，避免静默丢字段。
        SavedViewFilterCatalog.validateUpdate(
                existing.pageId(), existing.schemaVersion(), schemaVersion, filter, sort);
        if (isDefault) {
            repository.clearDefault(actor.tenantId(), actor.principalId(), PORTAL, existing.pageId());
        }
        Instant now = Instant.now(clock);
        SavedViewRepository.SavedViewRecord next = new SavedViewRepository.SavedViewRecord(
                existing.id(),
                existing.tenantId(),
                existing.principalId(),
                existing.portal(),
                existing.pageId(),
                normalizedName,
                existing.visibility(),
                existing.sharedScopeRef(),
                schemaVersion,
                SavedViewJson.write(filter),
                SavedViewJson.write(sort),
                SavedViewJson.write(columns),
                isDefault,
                existing.aggregateVersion() + 1,
                existing.createdAt(),
                now
        );
        if (!repository.update(next, expectedVersion)) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "保存视图版本冲突，请刷新后重试");
        }
        return SavedViewJson.toView(next);
    }

    @Override
    @Transactional
    public void delete(CurrentPrincipal actor, String correlationId, UUID savedViewId) {
        if (savedViewId == null) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "savedViewId 不能为空");
        }
        if (!repository.deleteOwned(actor.tenantId(), actor.principalId(), savedViewId)) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "保存视图不存在");
        }
    }

    @Override
    @Transactional
    public SavedView share(
            CurrentPrincipal actor,
            String correlationId,
            UUID savedViewId,
            long expectedVersion,
            SavedViewVisibility visibility,
            String sharedScopeRef
    ) {
        if (savedViewId == null) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "savedViewId 不能为空");
        }
        if (expectedVersion < 1) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "expectedVersion 无效");
        }
        if (visibility == null) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "visibility 不能为空");
        }
        SavedViewRepository.SavedViewRecord existing = repository
                .findOwned(actor.tenantId(), actor.principalId(), savedViewId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "保存视图不存在"));

        String normalizedScope = normalizeScope(visibility, sharedScopeRef, actor.tenantId());

        // 分享出 PRIVATE 需 HIGH capability；owner 收回 PRIVATE 始终允许（即使失权也可止损）。
        if (visibility != SavedViewVisibility.PRIVATE) {
            authorization.require(
                    actor,
                    AuthorizationRequest.tenantCapability(
                            SHARE_CAPABILITY, actor.tenantId(), "SavedView", savedViewId.toString()),
                    correlationId);
        }

        Instant now = Instant.now(clock);
        long nextVersion = existing.aggregateVersion() + 1;
        if (!repository.updateVisibility(
                actor.tenantId(),
                actor.principalId(),
                savedViewId,
                expectedVersion,
                visibility,
                normalizedScope,
                nextVersion,
                now)) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "保存视图版本冲突，请刷新后重试");
        }

        String action = visibility == SavedViewVisibility.PRIVATE
                ? "SAVED_VIEW_UNSHARED"
                : "SAVED_VIEW_SHARED";
        audit.append(new AuditEntry(
                UUID.randomUUID(),
                actor.tenantId(),
                actor.principalId(),
                action,
                visibility == SavedViewVisibility.PRIVATE ? null : SHARE_CAPABILITY,
                "SavedView",
                savedViewId.toString(),
                "ALLOW",
                List.of(),
                "n/a",
                "SUCCESS",
                null,
                "visibility=" + visibility.name()
                        + (normalizedScope == null ? "" : ";scope=" + normalizedScope),
                correlationId,
                now
        ));

        return SavedViewJson.toView(new SavedViewRepository.SavedViewRecord(
                existing.id(),
                existing.tenantId(),
                existing.principalId(),
                existing.portal(),
                existing.pageId(),
                existing.name(),
                visibility,
                normalizedScope,
                existing.schemaVersion(),
                existing.filterJson(),
                existing.sortJson(),
                existing.columnJson(),
                existing.isDefault(),
                nextVersion,
                existing.createdAt(),
                now
        ));
    }

    private String normalizeScope(SavedViewVisibility visibility, String sharedScopeRef, String tenantId) {
        return switch (visibility) {
            case PRIVATE, TENANT -> {
                if (sharedScopeRef != null && !sharedScopeRef.isBlank()) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                            visibility + " 共享不得提供 sharedScopeRef");
                }
                yield null;
            }
            case ROLE -> {
                String ref = requireText(sharedScopeRef, "sharedScopeRef");
                UUID roleId;
                try {
                    roleId = UUID.fromString(ref);
                } catch (IllegalArgumentException ex) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "sharedScopeRef 必须为 roleId UUID");
                }
                if (!activeRoles.roleExists(tenantId, roleId)) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "共享目标角色不存在或未激活");
                }
                yield roleId.toString();
            }
        };
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, field + " 不能为空");
        }
        return value.trim();
    }
}
