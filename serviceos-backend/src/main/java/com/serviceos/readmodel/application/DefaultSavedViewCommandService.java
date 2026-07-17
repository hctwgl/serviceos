package com.serviceos.readmodel.application;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.readmodel.api.SavedView;
import com.serviceos.readmodel.api.SavedViewCommandService;
import com.serviceos.readmodel.api.SavedViewFilterAst;
import com.serviceos.readmodel.api.SavedViewSortSpec;
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
 * 个人 SavedView 写事务：聚合与默认标记同事务提交。
 * 跨主体/跨租户按 RESOURCE_NOT_FOUND 失败关闭，不泄露他人偏好存在性。
 */
@Service
class DefaultSavedViewCommandService implements SavedViewCommandService {
    private static final String PORTAL = "ADMIN";

    private final SavedViewRepository repository;
    private final Clock clock;

    DefaultSavedViewCommandService(SavedViewRepository repository, Clock clock) {
        this.repository = repository;
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

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, field + " 不能为空");
        }
        return value.trim();
    }
}
