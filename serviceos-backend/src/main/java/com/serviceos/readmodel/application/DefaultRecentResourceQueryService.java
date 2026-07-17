package com.serviceos.readmodel.application;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.network.api.NetworkQueryService;
import com.serviceos.project.api.ProjectQueryService;
import com.serviceos.readmodel.api.RecentResourceItem;
import com.serviceos.readmodel.api.RecentResourcePage;
import com.serviceos.readmodel.api.RecentResourceQueryService;
import com.serviceos.readmodel.api.RecentResourceType;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.TaskDirectoryQueryService;
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
 * 个人最近访问读取：对每项经既有授权端口重新鉴权；失权项省略并在读路径删除。
 * <p>
 * 事务边界：不包外层事务。各授权端口自带只读事务；失权时抛出的 BusinessProblem
 * 若落在外层事务内会把连接标为 rollback-only，故列表编排保持非事务，陈旧行删除各自提交。
 * 失败关闭：未知 portal/type；整列表不因单项失权而 403。
 */
@Service
class DefaultRecentResourceQueryService implements RecentResourceQueryService {
    private static final Logger log = LoggerFactory.getLogger(DefaultRecentResourceQueryService.class);

    private final RecentResourceRepository repository;
    private final WorkOrderQueryService workOrders;
    private final TaskDirectoryQueryService tasks;
    private final ProjectQueryService projects;
    private final NetworkQueryService networks;
    private final Clock clock;

    DefaultRecentResourceQueryService(
            RecentResourceRepository repository,
            WorkOrderQueryService workOrders,
            TaskDirectoryQueryService tasks,
            ProjectQueryService projects,
            NetworkQueryService networks,
            Clock clock
    ) {
        this.repository = repository;
        this.workOrders = workOrders;
        this.tasks = tasks;
        this.projects = projects;
        this.networks = networks;
        this.clock = clock;
    }

    @Override
    // 不包外层事务：避免嵌套授权查询抛 BusinessProblem 污染编排层事务。
    public RecentResourcePage list(
            CurrentPrincipal actor, String correlationId, String portal, Integer limit
    ) {
        Objects.requireNonNull(actor, "actor must not be null");
        RecentResourceCatalog.requireAdminPortal(portal);
        int effectiveLimit = RecentResourceCatalog.normalizeLimit(limit);
        List<RecentResourceRepository.RecentResourceRecord> rows = repository.listByOwnerOrdered(
                actor.tenantId(),
                actor.principalId(),
                RecentResourceCatalog.PORTAL_ADMIN,
                RecentResourceCatalog.FETCH_OVERSCAN);

        List<RecentResourceItem> items = new ArrayList<>();
        for (RecentResourceRepository.RecentResourceRecord row : rows) {
            if (items.size() >= effectiveLimit) {
                break;
            }
            AccessOutcome outcome = reauthorize(actor, correlationId, row);
            if (outcome.accessible()) {
                String display = outcome.displayRef() != null ? outcome.displayRef() : row.displayRef();
                RecentResourceType type = RecentResourceType.valueOf(row.resourceType());
                items.add(new RecentResourceItem(
                        type,
                        row.resourceId(),
                        row.pageId(),
                        display,
                        row.lastVisitedAt(),
                        RecentResourceCatalog.deepLink(type, row.resourceId())
                ));
            } else {
                // 读路径清理失权/不存在行，避免列表长期堆积幽灵项。
                repository.deleteOwned(
                        row.tenantId(),
                        row.principalId(),
                        row.portal(),
                        row.resourceType(),
                        row.resourceId());
                log.info(
                        "最近访问失权已清理 tenant={} principal={} type={} resourceId={} corr={}",
                        actor.tenantId(),
                        actor.principalId(),
                        row.resourceType(),
                        row.resourceId(),
                        correlationId);
            }
        }
        return new RecentResourcePage(items, clock.instant());
    }

    private AccessOutcome reauthorize(
            CurrentPrincipal actor,
            String correlationId,
            RecentResourceRepository.RecentResourceRecord row
    ) {
        RecentResourceType type;
        try {
            type = RecentResourceType.valueOf(row.resourceType());
        } catch (IllegalArgumentException ex) {
            return AccessOutcome.denied();
        }
        UUID id;
        try {
            id = UUID.fromString(row.resourceId());
        } catch (IllegalArgumentException ex) {
            return AccessOutcome.denied();
        }
        try {
            return switch (type) {
                case WORK_ORDER -> {
                    var detail = workOrders.get(actor, correlationId, id);
                    String label = detail.workOrder().externalOrderCode() != null
                            ? detail.workOrder().externalOrderCode()
                            : RecentResourceCatalog.fallbackLabel(type, row.resourceId());
                    yield AccessOutcome.ok(sanitizeOrFallback(type, row.resourceId(), label));
                }
                case TASK -> {
                    var detail = tasks.get(actor, correlationId, id);
                    String label = detail.task().taskType() != null
                            ? detail.task().taskType()
                            : RecentResourceCatalog.fallbackLabel(type, row.resourceId());
                    yield AccessOutcome.ok(sanitizeOrFallback(type, row.resourceId(), label));
                }
                case PROJECT -> {
                    var detail = projects.get(actor, correlationId, id);
                    String label = detail.project().name() != null
                            ? detail.project().name()
                            : detail.project().code();
                    yield AccessOutcome.ok(sanitizeOrFallback(type, row.resourceId(), label));
                }
                case NETWORK -> {
                    var view = networks.getServiceNetwork(actor, correlationId, id);
                    String label = view.networkName() != null ? view.networkName() : view.networkCode();
                    yield AccessOutcome.ok(sanitizeOrFallback(type, row.resourceId(), label));
                }
                case TECHNICIAN -> {
                    var view = networks.getTechnicianProfile(actor, correlationId, id);
                    String label = view.displayName() != null
                            ? view.displayName()
                            : RecentResourceCatalog.fallbackLabel(type, row.resourceId());
                    yield AccessOutcome.ok(sanitizeOrFallback(type, row.resourceId(), label));
                }
            };
        } catch (BusinessProblem problem) {
            if (problem.code() == ProblemCode.ACCESS_DENIED
                    || problem.code() == ProblemCode.RESOURCE_NOT_FOUND) {
                return AccessOutcome.denied();
            }
            throw problem;
        }
    }

    private static String sanitizeOrFallback(RecentResourceType type, String resourceId, String label) {
        try {
            return RecentResourceCatalog.sanitizeDisplayRef(type, resourceId, label);
        } catch (BusinessProblem ignored) {
            return RecentResourceCatalog.fallbackLabel(type, resourceId);
        }
    }

    private record AccessOutcome(boolean accessible, String displayRef) {
        static AccessOutcome ok(String displayRef) {
            return new AccessOutcome(true, displayRef);
        }

        static AccessOutcome denied() {
            return new AccessOutcome(false, null);
        }
    }
}
