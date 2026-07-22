package com.serviceos.readmodel.application;

import com.serviceos.evidence.api.CorrectionCaseQueryService;
import com.serviceos.evidence.api.ReviewCaseQueryService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.operations.api.OperationalExceptionItem;
import com.serviceos.operations.api.OperationalExceptionPage;
import com.serviceos.operations.api.OperationalExceptionWorkbenchService;
import com.serviceos.readmodel.api.AdminWorkbenchView;
import com.serviceos.workorder.api.WorkOrderPage;
import com.serviceos.workorder.api.WorkOrderQuery;
import com.serviceos.workorder.api.WorkOrderQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultAdminWorkbenchQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-22T08:00:00Z");

    @Test
    @DisplayName("工作台应组合授权业务查询且不保存第二套任务状态")
    void shouldComposeAuthoritativeCounts() {
        WorkOrderQueryService workOrders = mock(WorkOrderQueryService.class);
        ReviewCaseQueryService reviews = mock(ReviewCaseQueryService.class);
        CorrectionCaseQueryService corrections = mock(CorrectionCaseQueryService.class);
        OperationalExceptionWorkbenchService exceptions = mock(OperationalExceptionWorkbenchService.class);
        CurrentPrincipal principal = new CurrentPrincipal(
                "admin-1", "tenant-1", CurrentPrincipal.PrincipalType.USER, "admin-web", Set.of());

        when(reviews.count(any(), anyString(), any())).thenReturn(5);
        when(corrections.count(any(), anyString(), any())).thenReturn(3);
        when(workOrders.list(any(), anyString(), any())).thenAnswer(invocation -> {
            WorkOrderQuery query = invocation.getArgument(2);
            int total = switch (String.valueOf(query.currentStageCode())) {
                case "PILOT_DISPATCH" -> 4;
                case "CLIENT_CALLBACK" -> 6;
                default -> "OPEN".equals(query.slaRisk()) ? 2 : 0;
            };
            return new WorkOrderPage(List.of(), null, NOW, null, null, total, false);
        });
        when(exceptions.list(any(), anyString(), any())).thenReturn(
                new OperationalExceptionPage(List.of(exceptionItem(), exceptionItem()), null));

        var service = new DefaultAdminWorkbenchQueryService(
                workOrders, reviews, corrections, exceptions, Clock.fixed(NOW, ZoneOffset.UTC));
        AdminWorkbenchView result = service.get(principal, "corr-workbench");

        assertThat(result.reviewCount()).isEqualTo(5);
        assertThat(result.correctionCount()).isEqualTo(3);
        assertThat(result.dispatchCount()).isEqualTo(4);
        assertThat(result.slaRiskCount()).isEqualTo(2);
        assertThat(result.exceptionCount()).isEqualTo(2);
        assertThat(result.waitingExternalCount()).isEqualTo(6);
        assertThat(result.priorityCount()).isEqualTo(16);
        assertThat(result.generatedAt()).isEqualTo(NOW);
    }

    private OperationalExceptionItem exceptionItem() {
        return new OperationalExceptionItem(
                UUID.randomUUID(), UUID.randomUUID(), "TASK", UUID.randomUUID().toString(), null,
                "INSTALLATION", "EXECUTION", "HIGH", "TEMPORARY_FAILURE", "OPEN",
                UUID.randomUUID(), UUID.randomUUID(), null, 1, 1, NOW, NOW,
                null, null, null, null, null, List.of("ACKNOWLEDGE"));
    }
}
