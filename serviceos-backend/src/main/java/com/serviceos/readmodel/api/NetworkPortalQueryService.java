package com.serviceos.readmodel.api;

import com.serviceos.evidence.api.CorrectionCaseView;
import com.serviceos.identity.api.CurrentPrincipal;

import java.util.UUID;

/** Network Portal 只读查询边界。 */
public interface NetworkPortalQueryService {
    NetworkPortalPage<NetworkPortalWorkOrderItem> listWorkOrders(
            CurrentPrincipal actor, String correlationId, String networkContextHeader);

    NetworkPortalPage<NetworkPortalTaskItem> listTasks(
            CurrentPrincipal actor, String correlationId, String networkContextHeader);

    NetworkPortalPage<NetworkPortalTechnicianItem> listTechnicians(
            CurrentPrincipal actor, String correlationId, String networkContextHeader);

    NetworkPortalPage<NetworkPortalCapacityItem> listCapacity(
            CurrentPrincipal actor, String correlationId, String networkContextHeader);

    NetworkPortalWorkbenchView workbench(
            CurrentPrincipal actor, String correlationId, String networkContextHeader);

    /**
     * 本网点整改队列。需要 ACTIVE membership + NETWORK scope {@code evidence.read}。
     * 仅聚合 ACTIVE NETWORK 责任任务上的 CorrectionCase。
     */
    NetworkPortalPage<NetworkPortalCorrectionItem> listCorrections(
            CurrentPrincipal actor,
            String correlationId,
            String networkContextHeader,
            String status,
            UUID taskId,
            Integer limit);

    /**
     * 本网点整改详情。先经 CorrectionCaseService.get，再校验 ACTIVE NETWORK 责任匹配上下文。
     */
    CorrectionCaseView getCorrection(
            CurrentPrincipal actor,
            String correlationId,
            String networkContextHeader,
            UUID correctionCaseId);
}
