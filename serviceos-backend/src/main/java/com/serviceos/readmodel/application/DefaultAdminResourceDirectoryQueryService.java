package com.serviceos.readmodel.application;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.network.api.NetworkQueryService;
import com.serviceos.network.api.PartnerOrganizationView;
import com.serviceos.network.api.ServiceNetworkCoverageQuery;
import com.serviceos.network.api.ServiceNetworkView;
import com.serviceos.network.api.TechnicianProfileView;
import com.serviceos.network.api.TechnicianQualificationView;
import com.serviceos.readmodel.api.AdminResourceDirectoryPage;
import com.serviceos.readmodel.api.AdminResourceDirectoryQueryService;
import com.serviceos.readmodel.api.AdminServiceNetworkDirectoryItem;
import com.serviceos.readmodel.api.AdminTechnicianDirectoryItem;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Admin 资源目录投影。
 *
 * <p>只通过 network 模块公开查询端口组合页面数据；不跨模块读取表，也不把运行时关系复制成新的事实。</p>
 */
@Service
class DefaultAdminResourceDirectoryQueryService implements AdminResourceDirectoryQueryService {
    private final NetworkQueryService networks;
    private final ServiceNetworkCoverageQuery coverages;
    private final Clock clock;

    DefaultAdminResourceDirectoryQueryService(
            NetworkQueryService networks,
            ServiceNetworkCoverageQuery coverages,
            Clock clock
    ) {
        this.networks = networks;
        this.coverages = coverages;
        this.clock = clock;
    }

    @Override
    public AdminResourceDirectoryPage load(CurrentPrincipal actor, String correlationId) {
        Instant asOf = clock.instant();
        List<ServiceNetworkView> networkItems = networks.listServiceNetworks(actor, correlationId, null).items();
        List<TechnicianProfileView> technicianItems = networks.listTechnicianProfiles(actor, correlationId).items();

        Map<UUID, String> partnerNames = networks.listPartnerOrganizations(actor, correlationId).items().stream()
                .collect(Collectors.toMap(PartnerOrganizationView::id, PartnerOrganizationView::name));
        Map<UUID, ServiceNetworkView> networkById = networkItems.stream()
                .collect(Collectors.toMap(ServiceNetworkView::id, Function.identity()));

        var activeCoverages = coverages.listActiveCoverageByNetworks(
                actor.tenantId(), networkItems.stream().map(item -> item.id().toString()).toList(), asOf);
        Map<UUID, List<String>> regionsByNetwork = activeCoverages.stream().collect(Collectors.groupingBy(
                item -> item.serviceNetworkId(),
                Collectors.mapping(item -> item.regionCode(), Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new), List::copyOf))));

        Map<UUID, List<UUID>> technicianNetworks = technicianItems.stream().collect(Collectors.toMap(
                TechnicianProfileView::id,
                profile -> networks.listNetworkTechnicianMemberships(actor, correlationId, null, profile.id())
                        .items().stream()
                        .filter(item -> "ACTIVE".equals(item.status()))
                        .map(item -> item.serviceNetworkId())
                        .toList()));
        Map<UUID, Long> technicianCounts = technicianNetworks.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        List<AdminServiceNetworkDirectoryItem> networkViews = networkItems.stream().map(item ->
                new AdminServiceNetworkDirectoryItem(
                        item.id(), item.networkCode(), item.networkName(),
                        requirePartnerName(partnerNames, item),
                        item.status(), regionsByNetwork.getOrDefault(item.id(), List.of()),
                        Math.toIntExact(technicianCounts.getOrDefault(item.id(), 0L)), item.updatedAt()))
                .toList();

        List<AdminTechnicianDirectoryItem> technicianViews = new ArrayList<>();
        for (TechnicianProfileView profile : technicianItems) {
            List<TechnicianQualificationView> qualifications = networks.listTechnicianQualifications(
                    actor, correlationId, profile.id()).items();
            List<String> networkNames = technicianNetworks.getOrDefault(profile.id(), List.of()).stream()
                    .map(networkById::get)
                    .filter(java.util.Objects::nonNull)
                    .map(ServiceNetworkView::networkName)
                    .distinct()
                    .toList();
            List<String> approved = qualifications.stream()
                    .filter(item -> "APPROVED".equals(item.status()))
                    .map(TechnicianQualificationView::qualificationCode)
                    .distinct()
                    .toList();
            int pending = Math.toIntExact(qualifications.stream()
                    .filter(item -> "PENDING".equals(item.status())).count());
            technicianViews.add(new AdminTechnicianDirectoryItem(
                    profile.id(), profile.displayName(), profile.status(), profile.supportedClientKinds(),
                    networkNames, approved, pending, profile.updatedAt()));
        }
        return new AdminResourceDirectoryPage(networkViews, technicianViews, asOf);
    }

    private static String requirePartnerName(Map<UUID, String> partnerNames, ServiceNetworkView network) {
        String name = partnerNames.get(network.partnerOrganizationId());
        if (name == null || name.isBlank()) {
            throw new IllegalStateException(
                    "服务网点关联的合作组织名称缺失，无法生成 Admin 资源目录：networkId=" + network.id());
        }
        return name;
    }
}
