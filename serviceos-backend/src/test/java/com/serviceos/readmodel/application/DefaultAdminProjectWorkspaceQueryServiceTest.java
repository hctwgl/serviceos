package com.serviceos.readmodel.application;

import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.configuration.api.ProjectFulfillmentProfileService;
import com.serviceos.configuration.api.ProjectFulfillmentProfileSummary;
import com.serviceos.configuration.api.ProjectFulfillmentUsageSummary;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.network.api.NetworkQueryService;
import com.serviceos.network.api.ServiceNetworkPage;
import com.serviceos.network.api.ServiceNetworkView;
import com.serviceos.project.api.ProjectClientDirectoryItem;
import com.serviceos.project.api.ProjectClientDirectoryPage;
import com.serviceos.project.api.ProjectDetail;
import com.serviceos.project.api.ProjectQueryService;
import com.serviceos.project.api.ProjectReferenceOptions;
import com.serviceos.project.api.ProjectRegionOption;
import com.serviceos.project.api.ProjectView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DefaultAdminProjectWorkspaceQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-22T08:00:00Z");

    @Test
    @DisplayName("项目工作区应组合中文范围、网点与履约配置概览")
    void shouldComposeProjectAndFulfillmentSummary() {
        Fixture fixture = fixture(AuthorizationDecision.allow());
        UUID profileId = UUID.randomUUID();
        when(fixture.fulfillment.list(fixture.actor, "corr-project", fixture.projectId)).thenReturn(List.of(
                new ProjectFulfillmentProfileSummary(
                        profileId, fixture.projectId, "HOME_CHARGING_SURVEY_INSTALL", "家充勘测安装",
                        "ACTIVE", 7, 3, 8, "V1", NOW, "7 个阶段", "安装 48 小时", 2, NOW)));
        when(fixture.fulfillment.usageSummary(fixture.actor, "corr-project", fixture.projectId))
                .thenReturn(new ProjectFulfillmentUsageSummary(fixture.projectId, 8, false, NOW));

        var result = fixture.service.get(fixture.actor, "corr-project", fixture.projectId);

        assertThat(result.clientName()).isEqualTo("比亚迪");
        assertThat(result.regionNames()).containsExactly("山东省", "济南市");
        assertThat(result.networkNames()).containsExactly("济南历下服务中心");
        assertThat(result.activeWorkOrderCount()).isEqualTo(8);
        assertThat(result.fulfillmentProfiles()).singleElement().satisfies(profile -> {
            assertThat(profile.serviceProductName()).isEqualTo("充电桩安装服务");
            assertThat(profile.activeVersion()).isEqualTo("V1");
            assertThat(profile.dataComplete()).isTrue();
        });
    }

    @Test
    @DisplayName("缺少履约配置权限时必须明确标记不可查看而不是返回空配置事实")
    void shouldSoftGateFulfillmentConfiguration() {
        Fixture fixture = fixture(AuthorizationDecision.deny("CAPABILITY_MISSING"));

        var result = fixture.service.get(fixture.actor, "corr-project", fixture.projectId);

        assertThat(result.configurationReadable()).isFalse();
        assertThat(result.fulfillmentProfiles()).isEmpty();
        assertThat(result.activeWorkOrderCount()).isNull();
        verifyNoInteractions(fixture.fulfillment);
    }

    private Fixture fixture(AuthorizationDecision decision) {
        ProjectQueryService projects = mock(ProjectQueryService.class);
        ProjectFulfillmentProfileService fulfillment = mock(ProjectFulfillmentProfileService.class);
        NetworkQueryService networks = mock(NetworkQueryService.class);
        AuthorizationService authorization = mock(AuthorizationService.class);
        CurrentPrincipal actor = new CurrentPrincipal(
                "admin-1", "tenant-1", CurrentPrincipal.PrincipalType.USER, "admin-web", Set.of());
        UUID projectId = UUID.randomUUID();
        UUID networkId = UUID.randomUUID();
        ProjectView project = new ProjectView(
                projectId, "tenant-1", "BYD-SD-HOME", "BYD", "比亚迪山东家充项目",
                LocalDate.parse("2026-01-01"), null, List.of("370000", "370100"),
                List.of(networkId.toString()), "ACTIVE", 1, NOW, null, null);
        when(projects.get(actor, "corr-project", projectId)).thenReturn(new ProjectDetail(project, NOW));
        when(projects.listClientDirectory(actor, "corr-project", "ALL"))
                .thenReturn(new ProjectClientDirectoryPage(
                        List.of(new ProjectClientDirectoryItem("BYD", "比亚迪", "ACTIVE")), NOW));
        when(projects.referenceOptions(actor, "corr-project")).thenReturn(new ProjectReferenceOptions(
                List.of(), List.of(
                        new ProjectRegionOption("370000", "山东省", 1),
                        new ProjectRegionOption("370100", "济南市", 1)), NOW));
        when(networks.listServiceNetworks(actor, "corr-project", null)).thenReturn(new ServiceNetworkPage(
                List.of(new ServiceNetworkView(
                        networkId, UUID.randomUUID(), "NET-JN-LX", "济南历下服务中心", "ACTIVE", 1,
                        NOW, NOW, null, null, null)), NOW));
        when(authorization.authorize(any(), any(), any())).thenReturn(decision);

        return new Fixture(
                actor, projectId, fulfillment,
                new DefaultAdminProjectWorkspaceQueryService(
                        projects, fulfillment, networks, authorization, Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    private record Fixture(
            CurrentPrincipal actor,
            UUID projectId,
            ProjectFulfillmentProfileService fulfillment,
            DefaultAdminProjectWorkspaceQueryService service
    ) {
    }
}
