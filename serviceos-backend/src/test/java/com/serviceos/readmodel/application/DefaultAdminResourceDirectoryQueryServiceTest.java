package com.serviceos.readmodel.application;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.network.api.NetworkQueryService;
import com.serviceos.network.api.NetworkTechnicianMembershipPage;
import com.serviceos.network.api.NetworkTechnicianMembershipView;
import com.serviceos.network.api.PartnerOrganizationPage;
import com.serviceos.network.api.PartnerOrganizationView;
import com.serviceos.network.api.ServiceNetworkCoverageQuery;
import com.serviceos.network.api.ServiceNetworkCoverageView;
import com.serviceos.network.api.ServiceNetworkPage;
import com.serviceos.network.api.ServiceNetworkView;
import com.serviceos.network.api.TechnicianProfilePage;
import com.serviceos.network.api.TechnicianProfileView;
import com.serviceos.network.api.TechnicianQualificationPage;
import com.serviceos.network.api.TechnicianQualificationView;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultAdminResourceDirectoryQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-22T08:00:00Z");

    @Test
    @DisplayName("资源目录应返回网点中文名称、覆盖区域和师傅有效资质")
    void shouldComposeServiceNetworksAndTechnicians() {
        NetworkQueryService networks = mock(NetworkQueryService.class);
        ServiceNetworkCoverageQuery coverages = mock(ServiceNetworkCoverageQuery.class);
        CurrentPrincipal actor = new CurrentPrincipal(
                "admin-1", "tenant-1", CurrentPrincipal.PrincipalType.USER, "admin-web", Set.of());
        UUID partnerId = UUID.randomUUID();
        UUID networkId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();

        when(networks.listPartnerOrganizations(actor, "corr-resource")).thenReturn(new PartnerOrganizationPage(
                List.of(new PartnerOrganizationView(
                        partnerId, "PARTNER-JN", "济南恒通新能源服务有限公司", "ACTIVE", 1, NOW, NOW)), NOW));
        when(networks.listServiceNetworks(actor, "corr-resource", null)).thenReturn(new ServiceNetworkPage(
                List.of(new ServiceNetworkView(
                        networkId, partnerId, "NET-JN-LX", "济南历下服务中心", "ACTIVE", 1,
                        NOW, NOW, null, null, null)), NOW));
        when(networks.listTechnicianProfiles(actor, "corr-resource")).thenReturn(new TechnicianProfilePage(
                List.of(new TechnicianProfileView(
                        technicianId, UUID.randomUUID(), "李师傅", "ACTIVE", List.of("TECHNICIAN_IOS"),
                        1, NOW, NOW, null, null, null)), NOW));
        when(coverages.listActiveCoverageByNetworks(eq("tenant-1"), any(), eq(NOW))).thenReturn(List.of(
                new ServiceNetworkCoverageView(UUID.randomUUID(), networkId, "BYD", "INSTALLATION", "370100")));
        when(networks.listNetworkTechnicianMemberships(
                eq(actor), eq("corr-resource"), isNull(), eq(technicianId)))
                .thenReturn(new NetworkTechnicianMembershipPage(List.of(new NetworkTechnicianMembershipView(
                        UUID.randomUUID(), networkId, technicianId, "ACTIVE", NOW.minusSeconds(3600), null,
                        "admin-1", NOW.minusSeconds(3600), null, null, null, 1)), NOW));
        when(networks.listTechnicianQualifications(actor, "corr-resource", technicianId))
                .thenReturn(new TechnicianQualificationPage(List.of(
                        new TechnicianQualificationView(
                                UUID.randomUUID(), technicianId, "HOME_CHARGING_INSTALLATION", "APPROVED",
                                NOW.minusSeconds(3600), null, "admin-1", NOW.minusSeconds(3600),
                                "reviewer-1", NOW, "资质有效", 1),
                        new TechnicianQualificationView(
                                UUID.randomUUID(), technicianId, "HIGH_VOLTAGE_OPERATION", "PENDING",
                                NOW, null, "admin-1", NOW, null, null, null, 1)), NOW));

        var service = new DefaultAdminResourceDirectoryQueryService(
                networks, coverages, Clock.fixed(NOW, ZoneOffset.UTC));
        var result = service.load(actor, "corr-resource");

        assertThat(result.asOf()).isEqualTo(NOW);
        assertThat(result.networks()).singleElement().satisfies(network -> {
            assertThat(network.networkName()).isEqualTo("济南历下服务中心");
            assertThat(network.partnerOrganizationName()).isEqualTo("济南恒通新能源服务有限公司");
            assertThat(network.regionCodes()).containsExactly("370100");
            assertThat(network.activeTechnicianCount()).isEqualTo(1);
        });
        assertThat(result.technicians()).singleElement().satisfies(technician -> {
            assertThat(technician.displayName()).isEqualTo("李师傅");
            assertThat(technician.networkNames()).containsExactly("济南历下服务中心");
            assertThat(technician.approvedQualificationCodes()).containsExactly("HOME_CHARGING_INSTALLATION");
            assertThat(technician.pendingQualificationCount()).isEqualTo(1);
        });
    }
}
