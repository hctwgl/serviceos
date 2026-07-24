package com.serviceos.readmodel.application;

import com.serviceos.evidence.api.CorrectionCaseQueryService;
import com.serviceos.evidence.api.CorrectionCaseQueueQuery;
import com.serviceos.evidence.api.ReviewCaseQueryService;
import com.serviceos.evidence.api.ReviewCaseQueueQuery;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.operations.api.OperationalExceptionPage;
import com.serviceos.operations.api.OperationalExceptionQuery;
import com.serviceos.operations.api.OperationalExceptionWorkbenchService;
import com.serviceos.readmodel.api.AdminWorkbenchQueryService;
import com.serviceos.readmodel.api.AdminWorkbenchView;
import com.serviceos.workorder.api.WorkOrderQuery;
import com.serviceos.workorder.api.WorkOrderQueryService;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * 组合现有权威查询形成 Admin 首页任务投影。
 *
 * <p>本服务故意不建立工作台写模型。各计数继续由工单、SLA、审核整改和运营异常的公开查询端口
 * 负责授权与范围过滤；任何下游拒绝都会向上传播，绝不把无权限或依赖故障显示成零。</p>
 */
@Service
final class DefaultAdminWorkbenchQueryService implements AdminWorkbenchQueryService {
    private static final int PAGE_SIZE = 100;

    private final WorkOrderQueryService workOrders;
    private final ReviewCaseQueryService reviews;
    private final CorrectionCaseQueryService corrections;
    private final OperationalExceptionWorkbenchService exceptions;
    private final Clock clock;

    DefaultAdminWorkbenchQueryService(
            WorkOrderQueryService workOrders,
            ReviewCaseQueryService reviews,
            CorrectionCaseQueryService corrections,
            OperationalExceptionWorkbenchService exceptions,
            Clock clock
    ) {
        this.workOrders = workOrders;
        this.reviews = reviews;
        this.corrections = corrections;
        this.exceptions = exceptions;
        this.clock = clock;
    }

    @Override
    public AdminWorkbenchView get(CurrentPrincipal principal, String correlationId) {
        int reviewCount = reviews.count(
                principal, correlationId, new ReviewCaseQueueQuery(null, "OPEN", null, null, null, 1));
        int correctionCount = corrections.count(
                principal, correlationId, new CorrectionCaseQueueQuery(null, "IN_PROGRESS", null, null, null, 1));
        int dispatchCount = countWorkOrders(
                principal, correlationId, null, "NETWORK_UNASSIGNED", null);
        int slaRiskCount = countWorkOrders(principal, correlationId, null, null, "OPEN");
        int waitingExternalCount = countWorkOrders(
                principal, correlationId, "CLIENT_CALLBACK", null, null);
        int exceptionCount = countOpenExceptions(principal, correlationId);

        // “今日优先处理”表达待处理事项数量而非工单去重数，避免工作台隐藏同一工单的多类风险。
        int priorityCount = reviewCount + correctionCount + dispatchCount + slaRiskCount + exceptionCount;
        return new AdminWorkbenchView(
                priorityCount,
                reviewCount,
                correctionCount,
                dispatchCount,
                slaRiskCount,
                exceptionCount,
                waitingExternalCount,
                dispatchCount,
                clock.instant());
    }

    private int countWorkOrders(
            CurrentPrincipal principal,
            String correlationId,
            String currentStageCode,
            String responsibilityStatus,
            String slaRisk
    ) {
        WorkOrderQuery query = new WorkOrderQuery(
                null, null, null, null,
                null, null, null, currentStageCode, null,
                null, null, responsibilityStatus, slaRisk,
                null, null, null, null, null, 1);
        return workOrders.list(principal, correlationId, query).totalCount();
    }

    private int countOpenExceptions(CurrentPrincipal principal, String correlationId) {
        int count = 0;
        String cursor = null;
        do {
            OperationalExceptionPage page = exceptions.list(
                    principal,
                    correlationId,
                    new OperationalExceptionQuery(null, "OPEN", null, null, null, null, cursor, PAGE_SIZE));
            count += page.items().size();
            cursor = page.nextCursor();
        } while (cursor != null);
        return count;
    }
}
