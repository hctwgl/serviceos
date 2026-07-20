package com.serviceos.dispatch.application;

import com.serviceos.audit.api.AuditEntry;
import com.serviceos.configuration.api.EffectiveDispatchClientKinds;
import com.serviceos.configuration.api.FrozenBundleClientCapabilityProbe;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.network.api.TechnicianDeclaredClientKindsQuery;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.TaskFulfillmentContext;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * M367 / ADR-088 A1-B：Manual 与 Network Portal assign/reassign 的 TECHNICIAN kind 硬拒绝。
 *
 * <p>复用 M366 Bundle 求交与师傅声明；资产全未定向时不拦截（与自动池一致）。
 * 拒绝写 DENY 审计后抛 {@code CLIENT_CAPABILITY_UNSUPPORTED}（HTTP 422）。</p>
 */
@Component
final class ManualTechnicianClientKindGate {
    static final String ERROR_CODE = "CLIENT_KIND_INCOMPATIBLE";
    private static final String ACTION = "SERVICE_DISPATCH_TECHNICIAN_CLIENT_KIND_REJECT";

    private final FrozenBundleClientCapabilityProbe clientCapabilityProbe;
    private final TechnicianDeclaredClientKindsQuery technicianKinds;
    private final DispatchClientKindDenialAuditWriter denialAudit;
    private final Clock clock;

    ManualTechnicianClientKindGate(
            FrozenBundleClientCapabilityProbe clientCapabilityProbe,
            TechnicianDeclaredClientKindsQuery technicianKinds,
            DispatchClientKindDenialAuditWriter denialAudit,
            Clock clock
    ) {
        this.clientCapabilityProbe = clientCapabilityProbe;
        this.technicianKinds = technicianKinds;
        this.denialAudit = denialAudit;
        this.clock = clock;
    }

    void requireCompatible(
            CurrentPrincipal principal,
            String correlationId,
            TaskFulfillmentContext task,
            String technicianAssigneeId
    ) {
        EffectiveDispatchClientKinds target = clientCapabilityProbe.resolveDispatchTargetClientKinds(
                principal.tenantId(),
                task.configurationBundleId(),
                task.configurationBundleDigest(),
                task.formRef());
        if (!target.applyFilter()) {
            return;
        }
        UUID profileId;
        try {
            profileId = UUID.fromString(technicianAssigneeId);
        } catch (IllegalArgumentException | NullPointerException ex) {
            reject(principal, correlationId, task.taskId(), technicianAssigneeId, target,
                    "师傅档案标识无效，无法校验 supportedClientKinds");
            return;
        }
        Optional<TechnicianDeclaredClientKindsQuery.DeclaredKinds> declared =
                technicianKinds.findDeclaredSupportedClientKinds(principal.tenantId(), profileId);
        if (declared.isEmpty()) {
            reject(principal, correlationId, task.taskId(), technicianAssigneeId, target,
                    "师傅档案不存在，无法校验 supportedClientKinds");
            return;
        }
        if (!DispatchClientKindCompatibility.matchesDeclaredClientKinds(
                declared.get().kinds(), target.targetKinds())) {
            reject(principal, correlationId, task.taskId(), technicianAssigneeId, target,
                    "师傅声明的 supportedClientKinds 与任务冻结 Bundle 定向目标无交集（目标="
                            + String.join(",", target.targetKinds()) + "）");
        }
    }

    private void reject(
            CurrentPrincipal principal,
            String correlationId,
            UUID taskId,
            String technicianAssigneeId,
            EffectiveDispatchClientKinds target,
            String detail
    ) {
        String evidence = "client-kind|" + String.join(",", target.targetKinds())
                + "|" + technicianAssigneeId;
        // REQUIRES_NEW：外层 manualAssign 因 BusinessProblem 回滚时仍保留拒绝审计。
        denialAudit.appendDenied(new AuditEntry(
                UUID.randomUUID(),
                principal.tenantId(),
                principal.principalId(),
                ACTION,
                "dispatch.assignment.manage",
                "Task",
                taskId.toString(),
                "DENY",
                List.of(),
                "dispatch-client-kind-gate-v1",
                "REJECTED",
                ERROR_CODE,
                Sha256.digest(evidence + "|" + detail),
                correlationId,
                clock.instant()));
        throw new BusinessProblem(ProblemCode.CLIENT_CAPABILITY_UNSUPPORTED, detail);
    }
}
