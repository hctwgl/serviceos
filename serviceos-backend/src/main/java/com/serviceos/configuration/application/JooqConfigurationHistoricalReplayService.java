package com.serviceos.configuration.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationHistoricalReplayReport;
import com.serviceos.configuration.api.ConfigurationHistoricalReplayService;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.ConfigurationSimulationOutcome;
import com.serviceos.configuration.api.ConfigurationSimulationReport;
import com.serviceos.configuration.api.ConfigurationWorkflowSimulationService;
import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.configuration.api.RunConfigurationHistoricalReplayCommand;
import com.serviceos.configuration.api.RunConfigurationSimulationCommand;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.jooq.generated.tables.CfgConfigurationBundle;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.CfgConfigurationBundle.CFG_CONFIGURATION_BUNDLE;

/**
 * 历史回放：只读取已发布 Bundle 冻结清单中的 WORKFLOW，再委托干跑模拟（jOOQ）。
 *
 * <p>不读取草稿、不读取当前通道激活、不写运行时表。</p>
 */
@Service
final class JooqConfigurationHistoricalReplayService implements ConfigurationHistoricalReplayService {
    private static final String WRITE = "configuration.draft.write";
    private static final String RESOURCE = "ConfigurationHistoricalReplay";

    private final DSLContext dsl;
    private final AuthorizationService authorization;
    private final ConfigurationService configurations;
    private final ConfigurationWorkflowSimulationService simulations;

    JooqConfigurationHistoricalReplayService(
            DSLContext dsl,
            AuthorizationService authorization,
            ConfigurationService configurations,
            ConfigurationWorkflowSimulationService simulations
    ) {
        this.dsl = dsl;
        this.authorization = authorization;
        this.configurations = configurations;
        this.simulations = simulations;
    }

    @Override
    @Transactional(readOnly = true)
    public ConfigurationHistoricalReplayReport replay(
            CurrentPrincipal principal,
            String correlationId,
            RunConfigurationHistoricalReplayCommand command
    ) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.bundleId(), "bundleId");
        authorization.require(principal, AuthorizationRequest.tenantCapability(
                WRITE, principal.tenantId(), RESOURCE, command.bundleId().toString()), correlationId);

        BundleRow bundle = loadPublishedBundle(principal.tenantId(), command.bundleId());
        List<ConfigurationAssetDefinition> workflows = configurations.listBundleAssets(
                principal.tenantId(), bundle.bundleId(), bundle.manifestDigest(),
                ConfigurationAssetType.WORKFLOW);
        if (workflows.isEmpty()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "冻结 Bundle 未包含 WORKFLOW，无法历史回放");
        }
        ConfigurationAssetDefinition workflow = selectWorkflow(workflows, command.workflowAssetKey());
        ExpressionContext context = command.context() == null
                ? new ExpressionContext(
                        new ExpressionContext.WorkOrderContext(null, null, null),
                        new ExpressionContext.RegionContext(null, null, null),
                        new ExpressionContext.TaskContext(null, null),
                        Map.of())
                : command.context();

        ConfigurationSimulationReport simulation = simulations.simulate(
                principal,
                correlationId,
                new RunConfigurationSimulationCommand(
                        ConfigurationAssetType.WORKFLOW,
                        workflow.assetKey(),
                        workflow.definitionJson(),
                        context,
                        command.maxSteps()));

        return new ConfigurationHistoricalReplayReport(
                bundle.bundleId(),
                bundle.bundleCode(),
                bundle.bundleVersion(),
                bundle.manifestDigest(),
                workflow.versionId(),
                workflow.assetKey(),
                workflow.semanticVersion(),
                simulation.outcome(),
                simulation.message(),
                simulation.steps());
    }

    private static ConfigurationAssetDefinition selectWorkflow(
            List<ConfigurationAssetDefinition> workflows,
            String workflowAssetKey
    ) {
        if (workflowAssetKey == null || workflowAssetKey.isBlank()) {
            if (workflows.size() != 1) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "Bundle 含多个 WORKFLOW，必须指定 workflowAssetKey");
            }
            return workflows.getFirst();
        }
        String key = workflowAssetKey.trim();
        return workflows.stream()
                .filter(asset -> key.equals(asset.assetKey()))
                .findFirst()
                .orElseThrow(() -> new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "冻结 Bundle 未命中 WORKFLOW assetKey=" + key));
    }

    private BundleRow loadPublishedBundle(String tenantId, UUID bundleId) {
        CfgConfigurationBundle b = CFG_CONFIGURATION_BUNDLE;
        return dsl.select(b.BUNDLE_ID, b.BUNDLE_CODE, b.BUNDLE_VERSION, b.MANIFEST_DIGEST)
                .from(b)
                .where(b.TENANT_ID.eq(tenantId))
                .and(b.BUNDLE_ID.eq(bundleId))
                .and(b.STATUS.eq("PUBLISHED"))
                .fetchOptional(record -> new BundleRow(
                        record.value1(), record.value2(), record.value3(), record.value4()))
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND,
                        "未找到已发布 Bundle: " + bundleId));
    }

    private record BundleRow(
            UUID bundleId,
            String bundleCode,
            String bundleVersion,
            String manifestDigest
    ) {
    }
}
