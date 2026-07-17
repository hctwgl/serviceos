package com.serviceos.network.application;

import com.serviceos.network.api.NetworkPortalTechnicianQuery;
import com.serviceos.network.api.NetworkPortalTechnicianView;
import com.serviceos.network.domain.NetworkTechnicianMembership;
import com.serviceos.network.domain.TechnicianProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Network Portal 师傅目录：仅 ACTIVE 关系；不做 network.read 门禁。 */
@Service
final class DefaultNetworkPortalTechnicianQuery implements NetworkPortalTechnicianQuery {
    private final NetworkDirectoryRepository directory;
    private final Clock clock;

    DefaultNetworkPortalTechnicianQuery(NetworkDirectoryRepository directory, Clock clock) {
        this.directory = directory;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NetworkPortalTechnicianView> listActiveTechnicians(String tenantId, UUID networkId) {
        Instant at = clock.instant();
        List<NetworkTechnicianMembership> memberships =
                directory.listTechnicianMemberships(tenantId, networkId, null);
        List<NetworkPortalTechnicianView> items = new ArrayList<>();
        for (NetworkTechnicianMembership membership : memberships) {
            if (membership.status() != NetworkTechnicianMembership.Status.ACTIVE) {
                continue;
            }
            if (membership.validFrom().isAfter(at)) {
                continue;
            }
            if (membership.validTo() != null && !membership.validTo().isAfter(at)) {
                continue;
            }
            Optional<TechnicianProfile> profile =
                    directory.findTechnicianProfile(tenantId, membership.technicianProfileId());
            if (profile.isEmpty()) {
                continue;
            }
            TechnicianProfile p = profile.get();
            items.add(new NetworkPortalTechnicianView(
                    membership.id(),
                    p.id(),
                    p.principalId(),
                    p.displayName(),
                    p.status().name(),
                    membership.status().name(),
                    membership.validFrom(),
                    membership.validTo(),
                    membership.version()));
        }
        return List.copyOf(items);
    }
}
