package com.serviceos.dispatch.infrastructure;

import com.serviceos.dispatch.api.ActiveServiceResponsibility;
import com.serviceos.dispatch.api.ServiceAssignmentSummary;
import com.serviceos.dispatch.application.ActiveServiceResponsibilityRepository;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
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

    @Override
    public Optional<ServiceAssignmentSummary> findSummary(String tenantId, UUID taskId) {
        return Optional.ofNullable(mapper.findSummary(tenantId, taskId)).map(this::summary);
    }

    private ActiveServiceResponsibility responsibility(Map<String, Object> row) {
        Object id = row.get("taskId");
        return new ActiveServiceResponsibility(
                id instanceof UUID uuid ? uuid : UUID.fromString(id.toString()),
                text(row, "networkId"), text(row, "technicianId"));
    }

    private ServiceAssignmentSummary summary(Map<String, Object> row) {
        Object id = row.get("taskId");
        return new ServiceAssignmentSummary(
                id instanceof UUID uuid ? uuid : UUID.fromString(id.toString()),
                text(row, "networkId"), instant(row.get("networkEffectiveFrom")),
                text(row, "networkReassignmentReasonCode"),
                text(row, "technicianId"), instant(row.get("technicianEffectiveFrom")),
                text(row, "technicianReassignmentReasonCode"));
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private static Instant instant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalArgumentException("unsupported time type: " + value.getClass().getName());
    }
}
