package com.serviceos.readmodel.application;

import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.network.api.NetworkQueryService;
import com.serviceos.network.api.ServiceNetworkPage;
import com.serviceos.network.api.ServiceNetworkView;
import com.serviceos.project.api.ProjectClientDirectoryItem;
import com.serviceos.project.api.ProjectClientDirectoryPage;
import com.serviceos.project.api.ProjectQueryService;
import com.serviceos.project.api.RegionCatalogItem;
import com.serviceos.project.api.RegionCatalogPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultAdminProjectCreationOptionsQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-22T08:00:00Z");

    @Test
    @DisplayName("新建项目选项应一次返回中文客户、行政区、有效网点和允许动作")
    void shouldComposeProjectCreationOptions() {
        ProjectQueryService projects = mock(ProjectQueryService.class);
        NetworkQueryService networks = mock(NetworkQueryService.class);
        AuthorizationService authorization = mock(AuthorizationService.class);
        CurrentPrincipal actor = new CurrentPrincipal(
                "admin-1", "tenant-1", CurrentPrincipal.PrincipalType.USER, "admin-web", Set.of());
        UUID networkId = UUID.randomUUID();

        when(projects.listClientDirectory(actor, "corr-create", "ACTIVE"))
                .thenReturn(new ProjectClientDirectoryPage(
                        List.of(new ProjectClientDirectoryItem("BYD", "比亚迪汽车", "ACTIVE")), NOW));
        when(projects.listRegionCatalog(actor, "corr-create", "*", "历城", null, 50))
                .thenReturn(new RegionCatalogPage(List.of(
                        new RegionCatalogItem("370100", "370000", "济南市", "CITY", 1, 1),
                        new RegionCatalogItem("370112", "370100", "历城区", "DISTRICT", 1, 0)), NOW));
        when(networks.listServiceNetworks(actor, "corr-create", null)).thenReturn(new ServiceNetworkPage(
                List.of(
                        new ServiceNetworkView(networkId, UUID.randomUUID(), "JN-ZL", "济南智联服务中心",
                                "ACTIVE", 1, NOW, NOW, null, null, null),
                        new ServiceNetworkView(UUID.randomUUID(), UUID.randomUUID(), "QD-OLD", "青岛停服网点",
                                "DEACTIVATED", 2, NOW, NOW, NOW, "admin", "合作终止")), NOW));
        when(authorization.authorize(any(), any(), any())).thenReturn(AuthorizationDecision.allow());

        var service = new DefaultAdminProjectCreationOptionsQueryService(
                projects, networks, authorization, Clock.fixed(NOW, ZoneOffset.UTC));
        var result = service.load(actor, "corr-create", " 历城 ");

        assertThat(result.clients()).singleElement()
                .satisfies(item -> assertThat(item.name()).isEqualTo("比亚迪汽车"));
        assertThat(result.regions()).extracting(item -> item.name())
                .containsExactly("济南市", "历城区");
        assertThat(result.networks()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(networkId);
            assertThat(item.name()).isEqualTo("济南智联服务中心");
        });
        assertThat(result.allowedActions()).containsExactly("CREATE_PROJECT");
    }
}
