package com.serviceos.identity.infrastructure;

import com.serviceos.identity.application.IdentityDirectoryQueryRepository;
import com.serviceos.identity.domain.SecurityPrincipal;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
        // MyBatis Map 结果在不同驱动/类型处理器下可能返回 Instant、OffsetDateTime 或 Timestamp。
        if (value instanceof Instant instantValue) {
            return instantValue;
        }
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toInstant(ZoneOffset.UTC);
        }
        throw new IllegalArgumentException("unsupported instant column type: " + value.getClass().getName());
    }

    private static Instant instantOrNull(Object value) {
        return value == null ? null : instant(value);
    }
}
