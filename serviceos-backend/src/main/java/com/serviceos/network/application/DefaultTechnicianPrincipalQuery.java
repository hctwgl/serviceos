package com.serviceos.network.application;

import com.serviceos.network.api.TechnicianPrincipalQuery;
import com.serviceos.network.domain.TechnicianProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** 师傅档案到有效登录主体的权威解析器。 */
@Service
final class DefaultTechnicianPrincipalQuery implements TechnicianPrincipalQuery {
    private final NetworkDirectoryRepository directory;

    DefaultTechnicianPrincipalQuery(NetworkDirectoryRepository directory) {
        this.directory = directory;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findActivePrincipalId(String tenantId, String technicianProfileId) {
        final UUID profileId;
        try {
            profileId = UUID.fromString(technicianProfileId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return Optional.empty();
        }
        return directory.findTechnicianProfile(tenantId, profileId)
                .filter(profile -> profile.status() == TechnicianProfile.Status.ACTIVE)
                .map(profile -> profile.principalId().toString());
    }
}
