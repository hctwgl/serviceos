package com.serviceos.dispatch.application;

import com.serviceos.network.api.TechnicianPrincipalQuery;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.ServiceAssignmentExecutorPrincipalResolver;
import org.springframework.stereotype.Service;

/** 以师傅档案为责任权威，向 Task 暴露有效登录主体。 */
@Service
final class DefaultServiceAssignmentExecutorPrincipalResolver
        implements ServiceAssignmentExecutorPrincipalResolver {
    private final TechnicianPrincipalQuery technicianPrincipals;

    DefaultServiceAssignmentExecutorPrincipalResolver(
            TechnicianPrincipalQuery technicianPrincipals
    ) {
        this.technicianPrincipals = technicianPrincipals;
    }

    @Override
    public String requireActivePrincipalId(String tenantId, String assigneeId) {
        return technicianPrincipals.findActivePrincipalId(tenantId, assigneeId)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.SERVICE_ASSIGNMENT_CONFLICT,
                        "Assigned technician does not map to an ACTIVE login principal"));
    }
}
