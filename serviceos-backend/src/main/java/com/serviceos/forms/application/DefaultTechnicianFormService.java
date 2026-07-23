package com.serviceos.forms.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.configuration.api.ClientCapabilityRuntimeGate;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.dispatch.api.TechnicianActiveAssignmentQuery;
import com.serviceos.forms.api.FormSubmissionService;
import com.serviceos.forms.api.FormSubmissionView;
import com.serviceos.forms.api.SubmitFormCommand;
import com.serviceos.forms.api.TaskFormDefinition;
import com.serviceos.forms.api.TaskFormQueryService;
import com.serviceos.forms.api.TechnicianFormService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.network.api.NetworkTechnicianMembershipView;
import com.serviceos.network.api.PrincipalNetworkAffiliationQuery;
import com.serviceos.network.api.TechnicianProfileView;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Technician Portal 适配层；表单版本、验证与事务不变量仍由既有 Forms 服务维护。 */
@Service
final class DefaultTechnicianFormService implements TechnicianFormService {
    private static final String CONTEXT_PREFIX = "TECHNICIAN|NETWORK|";
    private static final String TASK_READ_ASSIGNED = "task.readAssigned";

    private final PrincipalNetworkAffiliationQuery affiliations;
    private final TechnicianActiveAssignmentQuery assignments;
    private final TaskFulfillmentContextService tasks;
    private final TaskFormQueryService forms;
    private final FormSubmissionService submissions;
    private final AuthorizationService authorization;
    private final ClientCapabilityRuntimeGate clientCapabilityRuntimeGate;
    private final Clock clock;

    DefaultTechnicianFormService(
            PrincipalNetworkAffiliationQuery affiliations,
            TechnicianActiveAssignmentQuery assignments,
            TaskFulfillmentContextService tasks,
            TaskFormQueryService forms,
            FormSubmissionService submissions,
            AuthorizationService authorization,
            ClientCapabilityRuntimeGate clientCapabilityRuntimeGate,
            Clock clock
    ) {
        this.affiliations = affiliations;
        this.assignments = assignments;
        this.tasks = tasks;
        this.forms = forms;
        this.submissions = submissions;
        this.authorization = authorization;
        this.clientCapabilityRuntimeGate = clientCapabilityRuntimeGate;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskFormDefinition> listForTask(
            CurrentPrincipal principal,
            String correlationId,
            String technicianContextHeader,
            String clientKind,
            UUID taskId
    ) {
        requireCurrentTask(principal, correlationId, technicianContextHeader, taskId);
        List<TaskFormDefinition> definitions = forms.listForTask(principal, correlationId, taskId);
        for (TaskFormDefinition definition : definitions) {
            clientCapabilityRuntimeGate.requireCompatible(
                    clientKind, ConfigurationAssetType.FORM, definition.definitionJson(),
                    definition.supportedClientKinds());
        }
        return definitions;
    }

    @Override
    @Transactional
    public FormSubmissionView submit(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String technicianContextHeader,
            String clientKind,
            SubmitFormCommand command
    ) {
        requireCurrentTask(principal, metadata.correlationId(), technicianContextHeader, command.taskId());
        // 提交前复检：防止仅绕过 GET 或客户端本地忽略后写入不兼容配置。
        for (TaskFormDefinition definition : forms.listForTask(
                principal, metadata.correlationId(), command.taskId())) {
            if (definition.formVersionId().equals(command.formVersionId())) {
                clientCapabilityRuntimeGate.requireCompatible(
                        clientKind, ConfigurationAssetType.FORM, definition.definitionJson(),
                        definition.supportedClientKinds());
            }
        }
        return submissions.submit(principal, metadata, command);
    }

    /**
     * Portal Context、网点责任与当前师傅责任在委托领域服务前全部重验；领域服务随后还会重验
     * RUNNING/guard、form capability 和冻结版本，避免任一授权事实变化时继续提交。
     */
    private void requireCurrentTask(
            CurrentPrincipal principal, String correlationId, String header, UUID taskId
    ) {
        UUID networkId = parseContext(header);
        UUID principalId = principalUuid(principal);
        TechnicianProfileView profile = affiliations.findActiveTechnicianProfile(
                        principal.tenantId(), principalId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID,
                        "当前主体没有有效的 TechnicianProfile"));
        boolean activeMember = affiliations.listActiveTechnicianMemberships(
                        principal.tenantId(), profile.id(), clock.instant()).stream()
                .map(NetworkTechnicianMembershipView::serviceNetworkId)
                .anyMatch(networkId::equals);
        if (!activeMember) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID,
                    "当前主体不能使用请求的 Technician Portal 上下文");
        }
        authorization.require(principal, AuthorizationRequest.networkCapability(
                        TASK_READ_ASSIGNED, principal.tenantId(), "ServiceNetwork",
                        networkId.toString(), networkId.toString()), correlationId);

        TaskFulfillmentContext task = tasks.find(principal.tenantId(), taskId)
                .orElseThrow(DefaultTechnicianFormService::taskNotFound);
        List<String> assigneeIds = List.of(principalId.toString(), profile.id().toString());
        // 任务完成/未指派时无 ACTIVE 责任分派，responsiblePrincipalId 为 null；
        // 不可变 List.contains(null) 会抛 NPE（500），显式判空后按“非当前责任人”处理。
        boolean currentResponsible = task.responsiblePrincipalId() != null
                && assigneeIds.contains(task.responsiblePrincipalId());
        boolean sameNetwork = assignments.filterTaskIdsForNetwork(
                principal.tenantId(), networkId.toString(), List.of(taskId)).contains(taskId);
        if (!currentResponsible || !sameNetwork) {
            // 不区分任务存在、属于其他网点或已改派，防止 Portal 形成资源探测旁路。
            throw taskNotFound();
        }
    }

    private static BusinessProblem taskNotFound() {
        return new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "任务不存在");
    }

    private static UUID parseContext(String header) {
        if (header == null || header.isBlank()) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID, "缺少 X-Technician-Context");
        }
        String raw = header.trim();
        String uuid = raw.startsWith(CONTEXT_PREFIX) ? raw.substring(CONTEXT_PREFIX.length()) : raw;
        if (!raw.startsWith(CONTEXT_PREFIX) && raw.contains("|")) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID, "Technician Portal 上下文形态无效");
        }
        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException exception) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID, "Technician Portal 上下文形态无效");
        }
    }

    private static UUID principalUuid(CurrentPrincipal principal) {
        try {
            return UUID.fromString(principal.principalId());
        } catch (IllegalArgumentException exception) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID,
                    "当前主体无法形成 Technician Portal 上下文");
        }
    }
}
