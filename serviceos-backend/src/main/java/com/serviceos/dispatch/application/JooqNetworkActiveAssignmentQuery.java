package com.serviceos.dispatch.application;

import com.serviceos.dispatch.api.NetworkActiveAssignmentQuery;
import com.serviceos.dispatch.api.NetworkActiveAssignmentView;
import com.serviceos.jooq.generated.tables.DspServiceAssignment;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.serviceos.jooq.generated.tables.DspServiceAssignment.DSP_SERVICE_ASSIGNMENT;

/**
 * ACTIVE NETWORK 责任列表：仅读 dispatch 拥有表，不穿越工单/任务模块边界。
 */
@Service
final class JooqNetworkActiveAssignmentQuery implements NetworkActiveAssignmentQuery {
    private final DSLContext dsl;

    JooqNetworkActiveAssignmentQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NetworkActiveAssignmentView> listActiveForNetwork(String tenantId, String networkId) {
        DspServiceAssignment n = DSP_SERVICE_ASSIGNMENT.as("n");
        DspServiceAssignment t = DSP_SERVICE_ASSIGNMENT.as("t");
        return dsl.select(
                        n.SERVICE_ASSIGNMENT_ID,
                        n.WORK_ORDER_ID,
                        n.TASK_ID,
                        n.BUSINESS_TYPE,
                        n.EFFECTIVE_FROM,
                        t.ASSIGNEE_ID)
                .from(n)
                .leftJoin(t)
                .on(t.TENANT_ID.eq(n.TENANT_ID))
                .and(t.TASK_ID.eq(n.TASK_ID))
                .and(t.RESPONSIBILITY_LEVEL.eq("TECHNICIAN"))
                .and(t.STATUS.eq("ACTIVE"))
                .where(n.TENANT_ID.eq(tenantId))
                .and(n.RESPONSIBILITY_LEVEL.eq("NETWORK"))
                .and(n.ASSIGNEE_ID.eq(networkId))
                .and(n.STATUS.eq("ACTIVE"))
                .orderBy(n.EFFECTIVE_FROM.desc().nullsLast(), n.SERVICE_ASSIGNMENT_ID)
                .fetch(row -> new NetworkActiveAssignmentView(
                        row.value1(), row.value2(), row.value3(), row.value4(), row.value5(), row.value6()));
    }
}
