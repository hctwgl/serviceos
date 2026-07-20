package com.serviceos.readmodel.application;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.project.api.ProjectQueryService;
import com.serviceos.readmodel.api.FollowedProjectCommandService;
import com.serviceos.readmodel.api.FollowedProjectItem;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 关注/取消关注：先按 project.read 鉴权，再同事务 upsert/delete。
 * <p>
 * 幂等：同一主体重复 follow 仅刷新 followed_at 与 display_ref；unfollow 对不存在行静默成功。
 */
@Service
class DefaultFollowedProjectCommandService implements FollowedProjectCommandService {
    private final FollowedProjectRepository repository;
    private final ProjectQueryService projects;
    private final Clock clock;

    DefaultFollowedProjectCommandService(
            FollowedProjectRepository repository, ProjectQueryService projects, Clock clock
    ) {
        this.repository = repository;
        this.projects = projects;
        this.clock = clock;
    }

    @Override
    @Transactional
    public FollowedProjectItem follow(
            CurrentPrincipal actor,
            String correlationId,
            String portal,
            UUID projectId,
            String displayRefHint
    ) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(projectId, "projectId");
        FollowedProjectCatalog.requireAdminPortal(portal);

        var detail = projects.get(actor, correlationId, projectId);
        String label = detail.project().name() != null && !detail.project().name().isBlank()
                ? detail.project().name()
                : detail.project().code();
        if (displayRefHint != null && !displayRefHint.isBlank()) {
            label = displayRefHint;
        }
        String displayRef = FollowedProjectCatalog.sanitizeDisplayRef(projectId, label);
        Instant now = Instant.now(clock);
        Instant createdAt = repository.findOwned(
                        actor.tenantId(), actor.principalId(), FollowedProjectCatalog.PORTAL_ADMIN, projectId)
                .map(FollowedProjectRepository.FollowedProjectRecord::createdAt)
                .orElse(now);

        repository.upsert(new FollowedProjectRepository.FollowedProjectRecord(
                actor.tenantId(),
                actor.principalId(),
                FollowedProjectCatalog.PORTAL_ADMIN,
                projectId,
                displayRef,
                now,
                createdAt));

        // 关注写路径只回写身份与深链；角标由列表读模型实时聚合。
        return FollowedProjectItem.withoutBadges(
                projectId,
                displayRef,
                detail.project().code(),
                detail.project().clientId(),
                detail.project().status(),
                now,
                FollowedProjectCatalog.deepLink(projectId));
    }

    @Override
    @Transactional
    public void unfollow(CurrentPrincipal actor, String correlationId, String portal, UUID projectId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(projectId, "projectId");
        FollowedProjectCatalog.requireAdminPortal(portal);
        // 取消关注不要求仍可读该项目；否则失权后无法清理。
        repository.deleteOwned(
                actor.tenantId(), actor.principalId(), FollowedProjectCatalog.PORTAL_ADMIN, projectId);
        if (correlationId == null || correlationId.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "correlationId 不能为空");
        }
    }
}
