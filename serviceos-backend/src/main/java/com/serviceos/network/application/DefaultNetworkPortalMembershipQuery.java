package com.serviceos.network.application;

import com.serviceos.network.api.NetworkPortalMembershipQuery;
import com.serviceos.network.api.NetworkTechnicianMembershipView;
import com.serviceos.network.domain.NetworkTechnicianMembership;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Network Portal 师傅关系目录：按网点列出/按 ID 读取；不做 network.read / Portal 门禁。
 */
@Service
final class DefaultNetworkPortalMembershipQuery implements NetworkPortalMembershipQuery {
    private final NetworkDirectoryRepository directory;

    DefaultNetworkPortalMembershipQuery(NetworkDirectoryRepository directory) {
        this.directory = directory;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NetworkTechnicianMembershipView> listForNetwork(String tenantId, UUID networkId) {
        return directory.listTechnicianMemberships(tenantId, networkId, null).stream()
                .map(NetworkTechnicianMembership::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NetworkTechnicianMembershipView> findById(String tenantId, UUID membershipId) {
        return directory.findTechnicianMembership(tenantId, membershipId)
                .map(NetworkTechnicianMembership::toView);
    }
}
