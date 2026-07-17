package com.serviceos.operations.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.util.List;
import java.util.UUID;

public interface OperationalExceptionWorkbenchService {
    OperationalExceptionPage list(CurrentPrincipal principal, String correlationId, OperationalExceptionQuery query);

    OperationalExceptionItem get(CurrentPrincipal principal, String correlationId, UUID exceptionId);

    /**
     * 按任务列出运营异常（Network Portal fan-in）。
     * <p>
     * 鉴权请求同时携带 projectId 与 ACTIVE NETWORK id，使 NETWORK scope
     * {@code operations.exception.read} 可匹配。
     */
    List<OperationalExceptionItem> listForTask(
            CurrentPrincipal principal, String correlationId, UUID taskId);

    OperationalExceptionAcknowledgement acknowledge(
            CurrentPrincipal principal, CommandMetadata metadata, AcknowledgeOperationalExceptionCommand command);
}
