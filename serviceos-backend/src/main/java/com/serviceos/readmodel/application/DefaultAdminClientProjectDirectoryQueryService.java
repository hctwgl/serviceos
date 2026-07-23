package com.serviceos.readmodel.application;

import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.project.api.ProjectClientBrandItem;
import com.serviceos.project.api.ProjectClientDirectoryItem;
import com.serviceos.project.api.ProjectQuery;
import com.serviceos.project.api.ProjectQueryService;
import com.serviceos.project.api.ProjectView;
import com.serviceos.readmodel.api.AdminClientProjectDirectoryQueryService;
import com.serviceos.readmodel.api.AdminClientProjectDirectoryView;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 客户与项目目录投影。
 *
 * <p>页面只消费本投影，不根据 clientCode、regionCode 或项目标识在浏览器中关联多个领域目录。
 * 缺少必须展示的名称时明确标记数据不完整，不使用编码或内部标识兜底。</p>
 */
@Service
final class DefaultAdminClientProjectDirectoryQueryService
        implements AdminClientProjectDirectoryQueryService {
    private final ProjectQueryService projects;
    private final AuthorizationService authorization;
    private final Clock clock;

    DefaultAdminClientProjectDirectoryQueryService(
            ProjectQueryService projects,
            AuthorizationService authorization,
            Clock clock
    ) {
        this.projects = projects;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Override
    public AdminClientProjectDirectoryView load(CurrentPrincipal actor, String correlationId) {
        List<ProjectView> projectItems = projects.list(
                actor, correlationId, new ProjectQuery(null, null, null, null, 100)).items();
        List<ProjectClientDirectoryItem> clientItems = projects.listClientDirectory(
                actor, correlationId, "ALL").items();
        var references = projects.referenceOptions(actor, correlationId);

        Map<String, String> clientNames = clientItems.stream().collect(Collectors.toMap(
                ProjectClientDirectoryItem::clientCode,
                ProjectClientDirectoryItem::displayName,
                (left, right) -> left,
                LinkedHashMap::new));
        Map<String, String> regionNames = references.regions().stream()
                .filter(item -> !item.regionCode().equals(item.regionName()))
                .collect(Collectors.toMap(
                        item -> item.regionCode(), item -> item.regionName(), (left, right) -> left));
        Map<String, Long> projectCounts = projectItems.stream().collect(Collectors.groupingBy(
                ProjectView::clientId, Collectors.counting()));

        List<AdminClientProjectDirectoryView.ClientItem> clients = new ArrayList<>();
        for (ProjectClientDirectoryItem client : clientItems) {
            List<AdminClientProjectDirectoryView.BrandItem> brands = projects.listClientBrands(
                            actor, correlationId, client.clientCode(), "ALL").items().stream()
                    .sorted(java.util.Comparator.comparingInt(ProjectClientBrandItem::sortOrder))
                    .map(item -> new AdminClientProjectDirectoryView.BrandItem(
                            item.brandCode(), item.displayName(), item.status(), item.sortOrder()))
                    .toList();
            clients.add(new AdminClientProjectDirectoryView.ClientItem(
                    client.clientCode(), client.displayName(), client.status(), brands,
                    Math.toIntExact(projectCounts.getOrDefault(client.clientCode(), 0L))));
        }

        List<AdminClientProjectDirectoryView.ProjectItem> projectViews = projectItems.stream()
                .map(item -> toProjectItem(item, clientNames, regionNames))
                .toList();
        List<String> allowedActions = canMaintainCatalog(actor, correlationId)
                ? List.of("CREATE_CLIENT", "CREATE_BRAND")
                : List.of();
        return new AdminClientProjectDirectoryView(
                clients, projectViews, allowedActions, clock.instant());
    }

    private boolean canMaintainCatalog(CurrentPrincipal actor, String correlationId) {
        AuthorizationDecision decision = authorization.authorize(
                actor,
                AuthorizationRequest.tenantCapability(
                        "project.create", actor.tenantId(), "ProjectClientDirectory", actor.tenantId()),
                correlationId);
        return decision.effect() == AuthorizationDecision.Effect.ALLOW;
    }

    private static AdminClientProjectDirectoryView.ProjectItem toProjectItem(
            ProjectView project,
            Map<String, String> clientNames,
            Map<String, String> regionNames
    ) {
        String clientName = clientNames.get(project.clientId());
        List<String> missingRegions = project.regionCodes().stream()
                .filter(code -> !regionNames.containsKey(code))
                .toList();
        List<String> missing = new ArrayList<>();
        if (clientName == null || clientName.isBlank()) {
            missing.add("客户名称");
        }
        if (!missingRegions.isEmpty()) {
            missing.add("服务区域名称");
        }
        String configurationStatus = configurationStatus(
                project.publishedSchemeCount(), project.draftSchemeCount());
        return new AdminClientProjectDirectoryView.ProjectItem(
                project.id(), project.code(), project.name(), project.clientId(), clientName,
                project.startsOn(), project.endsOn(),
                project.regionCodes().stream().map(regionNames::get).filter(java.util.Objects::nonNull).toList(),
                project.networkIds().size(), project.status(), project.publishedSchemeCount(),
                project.draftSchemeCount(), configurationStatus, missing.isEmpty(),
                missing.isEmpty() ? null : "缺少页面展示字段：" + String.join("、", missing));
    }

    private static String configurationStatus(Integer published, Integer draft) {
        if (published == null || draft == null) {
            return "NO_PERMISSION";
        }
        if (published > 0 && draft > 0) {
            return "UNPUBLISHED_CHANGES";
        }
        if (published > 0) {
            return "PUBLISHED";
        }
        if (draft > 0) {
            return "DRAFT";
        }
        return "NOT_CONFIGURED";
    }
}
