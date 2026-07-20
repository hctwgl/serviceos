package com.serviceos.readmodel.application;

import com.serviceos.evidence.api.CorrectionCaseQueryService;
import com.serviceos.evidence.api.CorrectionCaseQueueQuery;
import com.serviceos.evidence.api.ReviewCaseQueryService;
import com.serviceos.evidence.api.ReviewCaseQueueQuery;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.project.api.ProjectQueryService;
import com.serviceos.readmodel.api.FollowedProjectItem;
import com.serviceos.readmodel.api.FollowedProjectPage;
import com.serviceos.readmodel.api.FollowedProjectQueryService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.sla.api.SlaInstanceQuery;
import com.serviceos.sla.api.SlaQueryService;
import com.serviceos.workorder.api.WorkOrderQuery;
import com.serviceos.workorder.api.WorkOrderQueryService;
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
 * 角标计数 soft-gate：缺 workOrder/evidence/sla 读能力时对应字段为 null，不拖垮整页。
 * 不包外层事务，避免嵌套授权查询抛 BusinessProblem 污染编排层事务。
 */
@Service
class DefaultFollowedProjectQueryService implements FollowedProjectQueryService {
    private static final Logger log = LoggerFactory.getLogger(DefaultFollowedProjectQueryService.class);
    private static final int BADGE_LIMIT = 100;

    private final FollowedProjectRepository repository;
    private final ProjectQueryService projects;
    private final WorkOrderQueryService workOrders;
    private final ReviewCaseQueryService reviews;
    private final CorrectionCaseQueryService corrections;
    private final SlaQueryService sla;
    private final Clock clock;

    DefaultFollowedProjectQueryService(
            FollowedProjectRepository repository,
            ProjectQueryService projects,
            WorkOrderQueryService workOrders,
            ReviewCaseQueryService reviews,
            CorrectionCaseQueryService corrections,
            SlaQueryService sla,
            Clock clock
    ) {
        this.repository = repository;
        this.projects = projects;
        this.workOrders = workOrders;
        this.reviews = reviews;
        this.corrections = corrections;
        this.sla = sla;
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
                CountBadge workOrdersBadge = countActiveWorkOrders(actor, correlationId, row.projectId());
                CountBadge reviewsBadge = countOpenReviews(actor, correlationId, row.projectId());
                CountBadge correctionsBadge = countOpenCorrections(actor, correlationId, row.projectId());
                CountBadge slaBadge = countSlaBreached(actor, correlationId, row.projectId());
                Integer openTodo = sumTodos(reviewsBadge, correctionsBadge, slaBadge);
                items.add(new FollowedProjectItem(
                        row.projectId(),
                        FollowedProjectCatalog.sanitizeDisplayRef(row.projectId(), label),
                        detail.project().code(),
                        detail.project().clientId(),
                        detail.project().status(),
                        row.followedAt(),
                        FollowedProjectCatalog.deepLink(row.projectId()),
                        workOrdersBadge == null ? null : workOrdersBadge.count(),
                        workOrdersBadge == null ? null : workOrdersBadge.truncated(),
                        reviewsBadge == null ? null : reviewsBadge.count(),
                        reviewsBadge == null ? null : reviewsBadge.truncated(),
                        correctionsBadge == null ? null : correctionsBadge.count(),
                        correctionsBadge == null ? null : correctionsBadge.truncated(),
                        slaBadge == null ? null : slaBadge.count(),
                        slaBadge == null ? null : slaBadge.truncated(),
                        openTodo));
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

    private CountBadge countActiveWorkOrders(CurrentPrincipal actor, String correlationId, UUID projectId) {
        try {
            var page = workOrders.list(
                    actor,
                    correlationId,
                    new WorkOrderQuery(null, projectId, "ACTIVE", null, BADGE_LIMIT));
            return new CountBadge(page.items().size(), page.nextCursor() != null);
        } catch (BusinessProblem problem) {
            if (problem.code() == ProblemCode.ACCESS_DENIED) {
                return null;
            }
            throw problem;
        }
    }

    private CountBadge countOpenReviews(CurrentPrincipal actor, String correlationId, UUID projectId) {
        try {
            var page = reviews.list(
                    actor,
                    correlationId,
                    new ReviewCaseQueueQuery(projectId, "OPEN", null, null, null, BADGE_LIMIT));
            return new CountBadge(page.items().size(), page.nextCursor() != null);
        } catch (BusinessProblem problem) {
            if (problem.code() == ProblemCode.ACCESS_DENIED) {
                return null;
            }
            throw problem;
        }
    }

    private CountBadge countOpenCorrections(CurrentPrincipal actor, String correlationId, UUID projectId) {
        try {
            var page = corrections.list(
                    actor,
                    correlationId,
                    new CorrectionCaseQueueQuery(projectId, "OPEN", null, null, null, BADGE_LIMIT));
            return new CountBadge(page.items().size(), page.nextCursor() != null);
        } catch (BusinessProblem problem) {
            if (problem.code() == ProblemCode.ACCESS_DENIED) {
                return null;
            }
            throw problem;
        }
    }

    private CountBadge countSlaBreached(CurrentPrincipal actor, String correlationId, UUID projectId) {
        try {
            var page = sla.list(
                    actor,
                    correlationId,
                    new SlaInstanceQuery(projectId, "BREACHED", null, BADGE_LIMIT));
            return new CountBadge(page.items().size(), page.nextCursor() != null);
        } catch (BusinessProblem problem) {
            if (problem.code() == ProblemCode.ACCESS_DENIED) {
                return null;
            }
            throw problem;
        }
    }

    private static Integer sumTodos(CountBadge reviews, CountBadge corrections, CountBadge sla) {
        if (reviews == null && corrections == null && sla == null) {
            return null;
        }
        int total = 0;
        if (reviews != null) {
            total += reviews.count();
        }
        if (corrections != null) {
            total += corrections.count();
        }
        if (sla != null) {
            total += sla.count();
        }
        return total;
    }

    private record CountBadge(int count, boolean truncated) {
    }
}
