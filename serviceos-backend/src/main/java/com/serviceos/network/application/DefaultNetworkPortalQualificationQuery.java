package com.serviceos.network.application;

import com.serviceos.network.api.NetworkPortalQualificationQuery;
import com.serviceos.network.api.TechnicianQualificationView;
import com.serviceos.network.domain.NetworkTechnicianMembership;
import com.serviceos.network.domain.TechnicianQualification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Network Portal 资质目录：仅 ACTIVE 师傅关系上的资质；不做 network.read / Portal 门禁。
 */
@Service
final class DefaultNetworkPortalQualificationQuery implements NetworkPortalQualificationQuery {
    private final NetworkDirectoryRepository directory;
    private final Clock clock;

    DefaultNetworkPortalQualificationQuery(NetworkDirectoryRepository directory, Clock clock) {
        this.directory = directory;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TechnicianQualificationView> listForActiveTechnicians(String tenantId, UUID networkId) {
        Instant at = clock.instant();
        List<NetworkTechnicianMembership> memberships =
                directory.listTechnicianMemberships(tenantId, networkId, null);
        List<TechnicianQualificationView> items = new ArrayList<>();
        for (NetworkTechnicianMembership membership : memberships) {
            if (!isActiveMembership(membership, at)) {
                continue;
            }
            for (TechnicianQualification qualification :
                    directory.listQualifications(tenantId, membership.technicianProfileId())) {
                items.add(qualification.toView());
            }
        }
        return List.copyOf(items);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TechnicianQualificationView> findById(String tenantId, UUID qualificationId) {
        return directory.findQualification(tenantId, qualificationId).map(TechnicianQualification::toView);
    }

    private static boolean isActiveMembership(NetworkTechnicianMembership membership, Instant at) {
        if (membership.status() != NetworkTechnicianMembership.Status.ACTIVE) {
            return false;
        }
        if (membership.validFrom().isAfter(at)) {
            return false;
        }
        return membership.validTo() == null || membership.validTo().isAfter(at);
    }
}
