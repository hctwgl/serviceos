package com.serviceos.dispatch.application;

import com.serviceos.dispatch.api.ActiveServiceResponsibility;
import com.serviceos.dispatch.api.ActiveServiceResponsibilityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
final class DefaultActiveServiceResponsibilityService implements ActiveServiceResponsibilityService {
    private final ActiveServiceResponsibilityRepository repository;

    DefaultActiveServiceResponsibilityService(ActiveServiceResponsibilityRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ActiveServiceResponsibility> find(String tenantId, UUID taskId) {
        return repository.find(tenantId, taskId);
    }
}
