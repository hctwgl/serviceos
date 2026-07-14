package com.serviceos.dispatch.infrastructure;

import com.serviceos.dispatch.api.ActiveServiceResponsibility;
import com.serviceos.dispatch.application.ActiveServiceResponsibilityRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
final class MyBatisActiveServiceResponsibilityRepository
        implements ActiveServiceResponsibilityRepository {
    private final ActiveServiceResponsibilityMapper mapper;

    MyBatisActiveServiceResponsibilityRepository(ActiveServiceResponsibilityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ActiveServiceResponsibility> find(String tenantId, UUID taskId) {
        return Optional.ofNullable(mapper.find(tenantId, taskId)).map(this::responsibility);
    }

    private ActiveServiceResponsibility responsibility(Map<String, Object> row) {
        Object id = row.get("taskId");
        return new ActiveServiceResponsibility(
                id instanceof UUID uuid ? uuid : UUID.fromString(id.toString()),
                text(row, "networkId"), text(row, "technicianId"));
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }
}
