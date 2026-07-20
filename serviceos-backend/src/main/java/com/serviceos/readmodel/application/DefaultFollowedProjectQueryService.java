package com.serviceos.readmodel.application;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.project.api.ProjectQueryService;
import com.serviceos.readmodel.api.FollowedProjectItem;
import com.serviceos.readmodel.api.FollowedProjectPage;
import com.serviceos.readmodel.api.FollowedProjectQueryService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 关注项目列表：逐项按 project.read 重新鉴权；失权/不存在项省略并删除。
 * <p>
 * 不包外层事务，避免嵌套授权查询抛 BusinessProblem 污染编排层事务。
 */
@Service
class DefaultFollowedProjectQueryService implements FollowedProjectQueryService {
    private static final Logger log = LoggerFactory.getLogger(DefaultFollowedProjectQueryService.class);

    private final FollowedProjectRepository repository;
    private final ProjectQueryService projects;
    private final Clock clock;

    DefaultFollowedProjectQueryService(
            FollowedProjectRepository repository, ProjectQueryService projects, Clock clock
    ) {
        this.repository = repository;
        this.projects = projects;
        this.clock = clock;
    }

    @Override
    public FollowedProjectPage list(
            CurrentPrincipal actor, String correlationId, String portal, Integer limit
    ) {
        Objects.requireNonNull(actor, "actor");
        FollowedProjectCatalog.requireAdminPortal(portal);
        int effectiveLimit = FollowedProjectCatalog.normalizeLimit(limit);
        List<FollowedProjectRepository.FollowedProjectRecord> rows = repository.listByOwnerOrdered(
                actor.tenantId(),
                actor.principalId(),
                FollowedProjectCatalog.PORTAL_ADMIN,
                FollowedProjectCatalog.FETCH_OVERSCAN);

        List<FollowedProjectItem> items = new ArrayList<>();
        for (FollowedProjectRepository.FollowedProjectRecord row : rows) {
            if (items.size() >= effectiveLimit) {
                break;
            }
            try {
                var detail = projects.get(actor, correlationId, row.projectId());
                String label = detail.project().name() != null && !detail.project().name().isBlank()
                        ? detail.project().name()
                        : detail.project().code();
                items.add(new FollowedProjectItem(
                        row.projectId(),
                        FollowedProjectCatalog.sanitizeDisplayRef(row.projectId(), label),
                        detail.project().code(),
                        detail.project().clientId(),
                        detail.project().status(),
                        row.followedAt(),
                        FollowedProjectCatalog.deepLink(row.projectId())));
            } catch (BusinessProblem problem) {
                if (problem.code() == ProblemCode.ACCESS_DENIED
                        || problem.code() == ProblemCode.RESOURCE_NOT_FOUND) {
                    repository.deleteOwned(
                            row.tenantId(), row.principalId(), row.portal(), row.projectId());
                    log.info(
                            "关注项目失权已清理 tenant={} principal={} projectId={} corr={}",
                            actor.tenantId(), actor.principalId(), row.projectId(), correlationId);
                    continue;
                }
                throw problem;
            }
        }
        return new FollowedProjectPage(items, clock.instant());
    }

    @Override
    public boolean isFollowed(
            CurrentPrincipal actor, String correlationId, String portal, UUID projectId
    ) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(projectId, "projectId");
        FollowedProjectCatalog.requireAdminPortal(portal);
        return repository.findOwned(
                        actor.tenantId(), actor.principalId(), FollowedProjectCatalog.PORTAL_ADMIN, projectId)
                .isPresent();
    }
}
