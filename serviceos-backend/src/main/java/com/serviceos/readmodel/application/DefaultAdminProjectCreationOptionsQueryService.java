package com.serviceos.readmodel.application;

import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.network.api.NetworkQueryService;
import com.serviceos.project.api.ProjectQueryService;
import com.serviceos.readmodel.api.AdminProjectCreationOptionsQueryService;
import com.serviceos.readmodel.api.AdminProjectCreationOptionsView;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;

/** Admin 新建项目抽屉组合查询；所有业务名称在服务端解析完成。 */
@Service
final class DefaultAdminProjectCreationOptionsQueryService
        implements AdminProjectCreationOptionsQueryService {
    private static final String CREATE = "project.create";

    private final ProjectQueryService projects;
    private final NetworkQueryService networks;
    private final AuthorizationService authorization;
    private final Clock clock;

    DefaultAdminProjectCreationOptionsQueryService(
            ProjectQueryService projects,
            NetworkQueryService networks,
            AuthorizationService authorization,
            Clock clock
    ) {
        this.projects = projects;
        this.networks = networks;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Override
    public AdminProjectCreationOptionsView load(
            CurrentPrincipal actor,
            String correlationId,
            String regionQuery
    ) {
        var clientOptions = projects.listClientDirectory(actor, correlationId, "ACTIVE").items().stream()
                .map(item -> new AdminProjectCreationOptionsView.ClientOption(
                        item.clientCode(), item.displayName()))
                .sorted(Comparator.comparing(AdminProjectCreationOptionsView.ClientOption::name))
                .toList();
        var regionOptions = projects.listRegionCatalog(
                        actor, correlationId, "*", normalizeRegionQuery(regionQuery), null, 50).items().stream()
                .map(item -> new AdminProjectCreationOptionsView.RegionOption(
                        item.regionCode(), item.regionName(), item.regionLevel(), item.parentCode()))
                .sorted(Comparator.comparing(AdminProjectCreationOptionsView.RegionOption::code))
                .toList();
        var networkOptions = networks.listServiceNetworks(actor, correlationId, null).items().stream()
                .filter(item -> "ACTIVE".equals(item.status()))
                .map(item -> new AdminProjectCreationOptionsView.NetworkOption(
                        item.id(), item.networkCode(), item.networkName(), item.status()))
                .sorted(Comparator.comparing(AdminProjectCreationOptionsView.NetworkOption::name))
                .toList();
        return new AdminProjectCreationOptionsView(
                clientOptions,
                regionOptions,
                networkOptions,
                canCreate(actor, correlationId) ? List.of("CREATE_PROJECT") : List.of(),
                clock.instant());
    }

    private static String normalizeRegionQuery(String regionQuery) {
        if (regionQuery == null || regionQuery.isBlank()) {
            return null;
        }
        return regionQuery.trim();
    }

    private boolean canCreate(CurrentPrincipal actor, String correlationId) {
        AuthorizationDecision decision = authorization.authorize(
                actor,
                AuthorizationRequest.tenantCapability(
                        CREATE, actor.tenantId(), "ProjectDirectory", actor.tenantId()),
                correlationId);
        return decision.effect() == AuthorizationDecision.Effect.ALLOW;
    }
}
