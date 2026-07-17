package com.serviceos.network.application;

import com.serviceos.network.api.NetworkMembershipView;
import com.serviceos.network.api.NetworkTechnicianMembershipView;
import com.serviceos.network.api.PrincipalNetworkAffiliationQuery;
import com.serviceos.network.api.TechnicianProfileView;
import com.serviceos.network.domain.NetworkMembership;
import com.serviceos.network.domain.NetworkTechnicianMembership;
import com.serviceos.network.domain.TechnicianProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 有效网点成员与师傅关系只读投影；供 Portal 上下文合成，不做目录能力门禁。 */
@Service
final class DefaultPrincipalNetworkAffiliationQuery implements PrincipalNetworkAffiliationQuery {
    private final NetworkDirectoryRepository directory;

    DefaultPrincipalNetworkAffiliationQuery(NetworkDirectoryRepository directory) {
        this.directory = directory;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NetworkMembershipView> listActiveNetworkMemberships(
            String tenantId, UUID principalId, Instant at
    ) {
        return directory.listMemberships(tenantId, null, principalId).stream()
                .filter(membership -> membership.status() == NetworkMembership.Status.ACTIVE)
                .filter(membership -> !membership.validFrom().isAfter(at))
                .filter(membership -> membership.validTo() == null || membership.validTo().isAfter(at))
                .map(NetworkMembership::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TechnicianProfileView> findActiveTechnicianProfile(String tenantId, UUID principalId) {
        return directory.findTechnicianProfileByPrincipal(tenantId, principalId)
                .filter(profile -> profile.status() == TechnicianProfile.Status.ACTIVE)
                .map(TechnicianProfile::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NetworkTechnicianMembershipView> listActiveTechnicianMemberships(
            String tenantId, UUID technicianProfileId, Instant at
    ) {
        return directory.listTechnicianMemberships(tenantId, null, technicianProfileId).stream()
                .filter(membership -> membership.status() == NetworkTechnicianMembership.Status.ACTIVE)
                .filter(membership -> !membership.validFrom().isAfter(at))
                .filter(membership -> membership.validTo() == null || membership.validTo().isAfter(at))
                .map(NetworkTechnicianMembership::toView)
                .toList();
    }
}
