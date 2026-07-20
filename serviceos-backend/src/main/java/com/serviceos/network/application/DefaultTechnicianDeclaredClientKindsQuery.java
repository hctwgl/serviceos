package com.serviceos.network.application;

import com.serviceos.network.api.TechnicianDeclaredClientKindsQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** 师傅声明只读：直接读档案，不做 network.read 门禁。 */
@Service
final class DefaultTechnicianDeclaredClientKindsQuery implements TechnicianDeclaredClientKindsQuery {
    private final NetworkDirectoryRepository directory;

    DefaultTechnicianDeclaredClientKindsQuery(NetworkDirectoryRepository directory) {
        this.directory = directory;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DeclaredKinds> findDeclaredSupportedClientKinds(
            String tenantId, UUID technicianProfileId
    ) {
        return directory.findTechnicianProfile(tenantId, technicianProfileId)
                .map(profile -> new DeclaredKinds(profile.supportedClientKinds()));
    }
}
