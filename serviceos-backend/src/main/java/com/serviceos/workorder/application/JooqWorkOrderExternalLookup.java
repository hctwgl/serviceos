package com.serviceos.workorder.application;

import com.serviceos.jooq.generated.tables.WoWorkOrder;
import com.serviceos.workorder.api.ExternalWorkOrderPointer;
import com.serviceos.workorder.api.WorkOrderExternalLookup;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.WoWorkOrder.WO_WORK_ORDER;

/** 按 tenant/client/externalOrder 定位工单指针。 */
@Service
final class JooqWorkOrderExternalLookup implements WorkOrderExternalLookup {
    private final DSLContext dsl;

    JooqWorkOrderExternalLookup(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
    }

    @Override
    public Optional<ExternalWorkOrderPointer> findByExternalOrder(
            String tenantId,
            String clientCode,
            String externalOrderCode
    ) {
        String safeTenant = required(tenantId, "tenantId");
        String safeClient = required(clientCode, "clientCode");
        String safeOrder = required(externalOrderCode, "externalOrderCode");
        WoWorkOrder wo = WO_WORK_ORDER;
        return dsl.select(wo.ID, wo.PROJECT_ID, wo.STATUS, wo.VERSION,
                        wo.CONFIGURATION_BUNDLE_ID, wo.CONFIGURATION_BUNDLE_DIGEST)
                .from(wo)
                .where(wo.TENANT_ID.eq(safeTenant))
                .and(wo.CLIENT_CODE.eq(safeClient))
                .and(wo.EXTERNAL_ORDER_CODE.eq(safeOrder))
                .fetchOptional()
                .map(row -> new ExternalWorkOrderPointer(
                        row.get(wo.ID),
                        row.get(wo.PROJECT_ID),
                        row.get(wo.STATUS),
                        row.get(wo.VERSION),
                        row.get(wo.CONFIGURATION_BUNDLE_ID),
                        row.get(wo.CONFIGURATION_BUNDLE_DIGEST)));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
