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

    /**
     * 本网点运营异常队列。需要 ACTIVE membership + NETWORK scope {@code operations.exception.read}。
     * 仅聚合 ACTIVE NETWORK 责任任务上的 OperationalException；Portal allowedActions 恒为空。
     */
    NetworkPortalPage<NetworkPortalExceptionItem> listExceptions(
            CurrentPrincipal actor,
            String correlationId,
            String networkContextHeader,
            String status,
            UUID taskId,
            String severity,
            Integer limit);

    /**
     * 本网点运营异常详情。先经 Workbench get，再校验 ACTIVE NETWORK 责任匹配上下文。
     */
    NetworkPortalExceptionItem getException(
            CurrentPrincipal actor,
            String correlationId,
            String networkContextHeader,
            UUID exceptionId);

    /**
     * 本网点师傅资质列表。需要 ACTIVE membership + NETWORK scope {@code technician.readOwnNetwork}。
     * 仅聚合 ACTIVE NetworkTechnicianMembership 师傅上的资质。
     */
    NetworkPortalPage<NetworkPortalQualificationItem> listQualifications(
            CurrentPrincipal actor,
            String correlationId,
            String networkContextHeader,
            String status,
            UUID technicianProfileId,
            Integer limit);

    /**
     * 本网点师傅资质详情。资质所属师傅须对本网点持有 ACTIVE 关系，否则 ACCESS_DENIED。
     */
    NetworkPortalQualificationItem getQualification(
            CurrentPrincipal actor,
            String correlationId,
            String networkContextHeader,
            UUID qualificationId);

    /**
     * 本网点师傅关系列表。需要 ACTIVE membership + NETWORK scope {@code technician.readOwnNetwork}。
     * 仅返回 serviceNetworkId = 上下文网点的关系；status 默认 ACTIVE。
     */
    NetworkPortalPage<NetworkPortalMembershipItem> listMemberships(
            CurrentPrincipal actor,
            String correlationId,
            String networkContextHeader,
            String status,
            UUID technicianProfileId,
            Integer limit);

    /**
     * 本网点师傅关系详情。serviceNetworkId 必须等于上下文网点，否则 ACCESS_DENIED。
     */
    NetworkPortalMembershipItem getMembership(
            CurrentPrincipal actor,
            String correlationId,
            String networkContextHeader,
            UUID membershipId);
}
