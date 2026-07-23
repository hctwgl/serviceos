package com.serviceos.readmodel.application;

import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.configuration.api.ProjectFulfillmentProfileService;
import com.serviceos.configuration.api.ProjectFulfillmentProfileSummary;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.network.api.NetworkQueryService;
import com.serviceos.project.api.ProjectQueryService;
import com.serviceos.project.api.ProjectView;
import com.serviceos.readmodel.api.AdminProjectWorkspaceQueryService;
import com.serviceos.readmodel.api.AdminProjectWorkspaceView;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 项目详情工作区组合服务。
 *
 * <p>项目基本信息始终按 project.read 返回；履约配置是独立软门禁，未授权时明确标记不可查看，
 * 不能用空配置伪装成“尚未配置”。所有名称只来自权威目录。</p>
 */
@Service
final class DefaultAdminProjectWorkspaceQueryService implements AdminProjectWorkspaceQueryService {
    private static final String FULFILLMENT_READ = "project.fulfillment.read";

    private final ProjectQueryService projects;
    private final ProjectFulfillmentProfileService fulfillmentProfiles;
    private final NetworkQueryService networks;
    private final AuthorizationService authorization;
    private final Clock clock;

    DefaultAdminProjectWorkspaceQueryService(
            ProjectQueryService projects,
            ProjectFulfillmentProfileService fulfillmentProfiles,
            NetworkQueryService networks,
            AuthorizationService authorization,
            Clock clock
    ) {
        this.projects = projects;
        this.fulfillmentProfiles = fulfillmentProfiles;
        this.networks = networks;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Override
    public AdminProjectWorkspaceView get(CurrentPrincipal actor, String correlationId, UUID projectId) {
        ProjectView project = projects.get(actor, correlationId, projectId).project();
        Map<String, String> clients = projects.listClientDirectory(actor, correlationId, "ALL").items().stream()
                .collect(Collectors.toMap(item -> item.clientCode(), item -> item.displayName()));
        Map<String, String> regions = projects.referenceOptions(actor, correlationId).regions().stream()
                .filter(item -> !item.regionCode().equals(item.regionName()))
                .collect(Collectors.toMap(item -> item.regionCode(), item -> item.regionName(), (left, right) -> left));
        Map<UUID, String> networkNames = networks.listServiceNetworks(actor, correlationId, null).items().stream()
                .collect(Collectors.toMap(item -> item.id(), item -> item.networkName()));

        List<String> missing = new ArrayList<>();
        String clientName = clients.get(project.clientId());
        if (clientName == null || clientName.isBlank()) {
            missing.add("客户名称");
        }
        List<String> regionLabels = project.regionCodes().stream()
                .map(regions::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (regionLabels.size() != project.regionCodes().size()) {
            missing.add("服务区域名称");
        }
        List<String> networkLabels = project.networkIds().stream()
                .map(DefaultAdminProjectWorkspaceQueryService::parseUuid)
                .map(networkNames::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (networkLabels.size() != project.networkIds().size()) {
            missing.add("参与网点名称");
        }

        boolean configurationReadable = canReadFulfillment(actor, correlationId, projectId);
        List<AdminProjectWorkspaceView.FulfillmentProfile> profileViews = List.of();
        Integer activeWorkOrderCount = null;
        Boolean activeWorkOrderCountTruncated = null;
        if (configurationReadable) {
            profileViews = fulfillmentProfiles.list(actor, correlationId, projectId).stream()
                    .map(DefaultAdminProjectWorkspaceQueryService::toProfile)
                    .toList();
            var usage = fulfillmentProfiles.usageSummary(actor, correlationId, projectId);
            activeWorkOrderCount = usage.activeWorkOrderCount();
            activeWorkOrderCountTruncated = usage.activeWorkOrderCountTruncated();
        }

        return new AdminProjectWorkspaceView(
                project.id(), project.code(), project.name(), clientName, project.status(), project.startsOn(),
                project.endsOn(), regionLabels, networkLabels, configurationReadable, profileViews,
                activeWorkOrderCount, activeWorkOrderCountTruncated, missing.isEmpty(),
                missing.isEmpty() ? null : "缺少页面展示字段：" + String.join("、", missing), clock.instant());
    }

    private boolean canReadFulfillment(CurrentPrincipal actor, String correlationId, UUID projectId) {
        AuthorizationDecision decision = authorization.authorize(
                actor,
                AuthorizationRequest.projectCapability(
                        FULFILLMENT_READ, actor.tenantId(), "Project", projectId.toString(), projectId.toString()),
                correlationId);
        return decision.effect() == AuthorizationDecision.Effect.ALLOW;
    }

    private static AdminProjectWorkspaceView.FulfillmentProfile toProfile(ProjectFulfillmentProfileSummary profile) {
        String serviceName = AdminProductLabels.service(profile.serviceProductCode());
        boolean complete = serviceName != null && !serviceName.isBlank();
        return new AdminProjectWorkspaceView.FulfillmentProfile(
                profile.profileId(), profile.profileName(), serviceName, profile.status(), profile.stageCount(),
                profile.formCount(), profile.evidenceCount(), profile.activeVersion(), profile.effectiveFrom(),
                profile.workflowSummary(), profile.slaSummary(), profile.updatedAt(), complete,
                complete ? null : "缺少页面展示字段：服务产品名称");
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
