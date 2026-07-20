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
import com.serviceos.task.api.TaskBlockedAction;
import com.serviceos.task.api.HumanTaskCompletionValidator;
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
 *
 * <p>M383：同时返回用户可理解的 blockedActions，解释为何领取/启动/完成暂不可用。</p>
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
    private final List<HumanTaskCompletionValidator> completionValidators;
    private final Clock clock;

    DefaultTaskAllowedActionQueryService(
            TaskDirectoryQueryService taskDirectory,
            TaskDirectoryQueryRepository taskQueries,
            AuthorizationService authorization,
            List<HumanTaskCompletionValidator> completionValidators,
            Clock clock
    ) {
        this.taskDirectory = taskDirectory;
        this.taskQueries = taskQueries;
        this.authorization = authorization;
        this.completionValidators = List.copyOf(completionValidators);
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public TaskAllowedActions get(CurrentPrincipal principal, String correlationId, UUID taskId) {
        taskDirectory.get(principal, correlationId, taskId);
        TaskDirectoryQueryRepository.AllowedActionState state = taskQueries
                .findAllowedActionState(principal.tenantId(), taskId, principal.principalId())
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "任务不存在"));

        List<TaskAllowedAction> actions = new ArrayList<>();
        List<TaskBlockedAction> blocked = new ArrayList<>();
        if (!"HUMAN".equals(state.taskKind())) {
            return new TaskAllowedActions(state.version(), actions, blocked, clock.instant());
        }
        if (state.activeGuard()) {
            blocked.add(blocked(COMPLETE, "完成任务", List.of("当前任务处于执行保护中，请稍后再试")));
            return new TaskAllowedActions(state.version(), actions, blocked, clock.instant());
        }

        boolean canClaim = allowed(principal, correlationId, taskId, CLAIM);
        boolean canStart = allowed(principal, correlationId, taskId, START);
        boolean canComplete = allowed(principal, correlationId, taskId, COMPLETE);
        boolean canRelease = allowed(principal, correlationId, taskId, RELEASE);
        boolean currentOwner = principal.principalId().equals(state.claimedBy())
                && state.actorResponsible();

        if ("READY".equals(state.status())) {
            if (state.actorCandidate() && canClaim) {
                actions.add(action(CLAIM, "领取任务", null, List.of()));
            } else {
                List<String> reasons = new ArrayList<>();
                if (!state.actorCandidate()) {
                    reasons.add("当前用户不在候选处理人列表中");
                }
                if (!canClaim) {
                    reasons.add("当前用户没有领取权限");
                }
                blocked.add(blocked(CLAIM, "领取任务", reasons));
            }
            blocked.add(blocked(START, "启动任务", List.of("当前状态不允许启动，请先领取任务")));
            blocked.add(blocked(COMPLETE, "完成任务", List.of("当前状态不允许提交")));
        } else if ("CLAIMED".equals(state.status())) {
            if (currentOwner && canStart) {
                actions.add(action(START, "启动任务", null, List.of()));
            } else {
                List<String> reasons = new ArrayList<>();
                if (!currentOwner) {
                    reasons.add("当前工单已由其他人员处理");
                }
                if (!canStart) {
                    reasons.add("当前用户没有启动权限");
                }
                blocked.add(blocked(START, "启动任务", reasons));
            }
            if (currentOwner && canRelease) {
                actions.add(action(
                        RELEASE,
                        "释放任务",
                        "#/components/schemas/ReleaseHumanTaskRequest",
                        List.of("REQUIRE_REASON")));
            }
            blocked.add(blocked(COMPLETE, "完成任务", List.of("当前状态不允许提交，请先启动任务")));
        } else if ("RUNNING".equals(state.status())) {
            List<String> readiness = explainCompletionReadiness(principal.tenantId(), taskId);
            if (currentOwner && state.workflowNodeInstanceId() != null && canComplete
                    && readiness.isEmpty()) {
                actions.add(action(
                        COMPLETE,
                        "完成任务",
                        "#/components/schemas/CompleteHumanTaskRequest",
                        List.of("REQUIRE_RESULT")));
            } else {
                List<String> reasons = new ArrayList<>();
                if (!currentOwner) {
                    reasons.add("当前工单已由其他人员处理");
                }
                if (state.workflowNodeInstanceId() == null) {
                    reasons.add("当前状态不允许提交");
                }
                if (!canComplete) {
                    reasons.add("当前用户没有完成权限");
                }
                reasons.addAll(readiness);
                if (reasons.isEmpty()) {
                    reasons.add("完成条件尚未满足，请检查表单与必传资料");
                }
                blocked.add(blocked(COMPLETE, "完成任务", reasons));
            }
        } else {
            blocked.add(blocked(CLAIM, "领取任务", List.of("当前状态不允许领取：" + state.status())));
            blocked.add(blocked(COMPLETE, "完成任务", List.of("当前状态不允许提交：" + state.status())));
        }
        return new TaskAllowedActions(state.version(), actions, blocked, clock.instant());
    }

    private boolean allowed(
            CurrentPrincipal principal, String correlationId, UUID taskId, String capability) {
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

    private List<String> explainCompletionReadiness(String tenantId, UUID taskId) {
        List<String> reasons = new ArrayList<>();
        for (HumanTaskCompletionValidator validator : completionValidators) {
            reasons.addAll(validator.explainBlockingReasons(tenantId, taskId));
        }
        return reasons;
    }

    private static TaskBlockedAction blocked(String code, String label, List<String> reasons) {
        return new TaskBlockedAction(code, label, reasons);
    }
}
