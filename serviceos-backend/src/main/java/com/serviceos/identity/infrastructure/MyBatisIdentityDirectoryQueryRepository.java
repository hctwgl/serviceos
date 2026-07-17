package com.serviceos.identity.infrastructure;

import com.serviceos.identity.application.IdentityDirectoryQueryRepository;
import com.serviceos.identity.domain.SecurityPrincipal;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
final class MyBatisIdentityDirectoryQueryRepository implements IdentityDirectoryQueryRepository {
    private final IdentityDirectoryQueryMapper mapper;

    MyBatisIdentityDirectoryQueryRepository(IdentityDirectoryQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<SecurityPrincipal> findPage(
            String tenantId, String query, String status, String cursorName, UUID cursorId, int fetchSize
    ) {
        return mapper.findPage(tenantId, query, status, cursorName, cursorId, fetchSize)
                .stream().map(MyBatisIdentityDirectoryQueryRepository::principal).toList();
    }

    private static SecurityPrincipal principal(Map<String, Object> row) {
        return new SecurityPrincipal((UUID) row.get("principalId"), row.get("tenantId").toString(),
                SecurityPrincipal.Type.valueOf(row.get("principalType").toString()),
                SecurityPrincipal.Status.valueOf(row.get("principalStatus").toString()),
                ((Number) row.get("aggregateVersion")).longValue(), instant(row.get("createdAt")),
                instant(row.get("updatedAt")), instantOrNull(row.get("disabledAt")),
                string(row, "disabledBy"), string(row, "disabledReason"),
                row.get("displayName").toString(), string(row, "employeeNumber"),
                ((Number) row.get("profileVersion")).longValue());
    }

    private static String string(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private static Instant instant(Object value) {
        return value instanceof OffsetDateTime dateTime ? dateTime.toInstant() : (Instant) value;
    }

    private static Instant instantOrNull(Object value) {
        return value == null ? null : instant(value);
    }
}
