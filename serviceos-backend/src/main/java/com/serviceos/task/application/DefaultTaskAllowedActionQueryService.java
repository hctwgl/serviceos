package com.serviceos.task.application;

import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.TaskAllowedAction;
import com.serviceos.task.api.TaskAllowedActionQueryService;
import com.serviceos.task.api.TaskAllowedActions;
import com.serviceos.task.api.TaskDirectoryQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 将现有人工 Task 写命令的实时前置事实投影为 Portal 动作。
 *
 * <p>本服务先复用 M70 读取边界，随后在 Task 自有表中一次读取状态、责任与 guard。动作缺失不写拒绝
 * 审计，因为它只是页面渲染结果；后续命令仍会重新执行授权、乐观锁、幂等和完成条件校验。</p>
 */
@Service
final class DefaultTaskAllowedActionQueryService implements TaskAllowedActionQueryService {
    private static final String CLAIM = "task.claim";
    private static final String START = "task.start";
    private static final String COMPLETE = "task.complete";
    private static final String RELEASE = "task.release";

    private final TaskDirectoryQueryService taskDirectory;
    private final TaskDirectoryQueryRepository taskQueries;
    private final AuthorizationService authorization;
    private final Clock clock;

    DefaultTaskAllowedActionQueryService(
            TaskDirectoryQueryService taskDirectory,
            TaskDirectoryQueryRepository taskQueries,
            AuthorizationService authorization,
            Clock clock
    ) {
        this.taskDirectory = taskDirectory;
        this.taskQueries = taskQueries;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public TaskAllowedActions get(CurrentPrincipal principal, String correlationId, UUID taskId) {
        // 先完成 tenant 隔离和 task.read 鉴权，避免动作查询泄露任务是否存在。
        taskDirectory.get(principal, correlationId, taskId);
        TaskDirectoryQueryRepository.AllowedActionState state = taskQueries
                .findAllowedActionState(principal.tenantId(), taskId, principal.principalId())
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "任务不存在"));

        List<TaskAllowedAction> actions = new ArrayList<>();
        if ("HUMAN".equals(state.taskKind()) && !state.activeGuard()) {
            if ("READY".equals(state.status()) && state.actorCandidate()
                    && allowed(principal, correlationId, taskId, CLAIM)) {
                actions.add(action(CLAIM, "领取任务", null, List.of()));
            }
            boolean currentOwner = principal.principalId().equals(state.claimedBy())
                    && state.actorResponsible();
            if ("CLAIMED".equals(state.status()) && currentOwner) {
                if (allowed(principal, correlationId, taskId, START)) {
                    actions.add(action(START, "启动任务", null, List.of()));
                }
                if (allowed(principal, correlationId, taskId, RELEASE)) {
                    actions.add(action(
                            RELEASE,
                            "释放任务",
                            "#/components/schemas/ReleaseHumanTaskRequest",
                            List.of("REQUIRE_REASON")));
                }
            }
            if ("RUNNING".equals(state.status()) && currentOwner
                    && state.workflowNodeInstanceId() != null
                    && allowed(principal, correlationId, taskId, COMPLETE)) {
                actions.add(action(
                        COMPLETE,
                        "完成任务",
                        "#/components/schemas/CompleteHumanTaskRequest",
                        List.of("REQUIRE_RESULT")));
            }
        }
        return new TaskAllowedActions(state.version(), actions, clock.instant());
    }

    private boolean allowed(
            CurrentPrincipal principal, String correlationId, UUID taskId, String capability) {
        // 与现有 M20/M21 写命令使用完全相同的 tenantCapability 请求，避免 UI 宣称命令无法执行的动作。
        AuthorizationDecision decision = authorization.authorize(
                principal,
                AuthorizationRequest.tenantCapability(
                        capability, principal.tenantId(), "Task", taskId.toString()),
                correlationId);
        return decision.effect() == AuthorizationDecision.Effect.ALLOW;
    }

    private static TaskAllowedAction action(
            String code, String label, String schemaRef, List<String> obligations) {
        return new TaskAllowedAction(code, label, schemaRef, obligations);
    }
}
