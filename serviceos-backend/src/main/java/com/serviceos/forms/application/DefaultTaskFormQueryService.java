package com.serviceos.forms.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.forms.api.TaskFormDefinition;
import com.serviceos.forms.api.TaskFormQueryService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 动态表单只读解析器。Task 已冻结 Workflow 节点的 formRef 与工单 Bundle 摘要，
 * 因而这里绝不查询“最新 FormVersion”，也不跨模块读取 WorkOrder/Workflow 内部表。
 */
@Service
final class DefaultTaskFormQueryService implements TaskFormQueryService {
    private static final String READ = "form.read";

    private final TaskFulfillmentContextService tasks;
    private final ConfigurationService configurations;
    private final AuthorizationService authorization;

    DefaultTaskFormQueryService(
            TaskFulfillmentContextService tasks,
            ConfigurationService configurations,
            AuthorizationService authorization
    ) {
        this.tasks = tasks;
        this.configurations = configurations;
        this.authorization = authorization;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskFormDefinition> listForTask(
            CurrentPrincipal principal, String correlationId, UUID taskId
    ) {
        TaskFulfillmentContext task = tasks.find(principal.tenantId(), taskId)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "Task does not exist"));
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ, principal.tenantId(), "Task", taskId.toString(), task.projectId().toString()),
                correlationId);
        if (task.formRef() == null) {
            return List.of();
        }

        List<ConfigurationAssetDefinition> matches = configurations.listBundleAssets(
                        principal.tenantId(), task.configurationBundleId(),
                        task.configurationBundleDigest(), ConfigurationAssetType.FORM)
                .stream()
                .filter(asset -> asset.assetKey().equals(task.formRef()))
                .toList();
        if (matches.size() != 1) {
            throw new BusinessProblem(
                    ProblemCode.TASK_STATE_CONFLICT,
                    "Task formRef must resolve to exactly one FormVersion in the frozen bundle");
        }
        ConfigurationAssetDefinition asset = matches.getFirst();
        return List.of(new TaskFormDefinition(
                taskId, asset.versionId(), asset.assetKey(), asset.semanticVersion(),
                asset.schemaVersion(), asset.definitionJson(), asset.contentDigest()));
    }
}
