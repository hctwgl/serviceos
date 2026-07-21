package com.serviceos.configuration.infrastructure;

import com.serviceos.configuration.api.PricingShadowSnapshotView;
import com.serviceos.configuration.application.PricingShadowSnapshotQueryRepository;
import com.serviceos.jooq.generated.tables.CfgCalculationSnapshot;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.CfgCalculationSnapshot.CFG_CALCULATION_SNAPSHOT;

/** cfg_calculation_snapshot SHADOW 只读适配器（jOOQ）。 */
@Repository
final class JooqPricingShadowSnapshotQueryRepository implements PricingShadowSnapshotQueryRepository {
    private final DSLContext dsl;

    JooqPricingShadowSnapshotQueryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<PricingShadowSnapshotView> listByWorkOrder(String tenantId, UUID workOrderId) {
        CfgCalculationSnapshot s = CFG_CALCULATION_SNAPSHOT;
        return dsl.select(s.SNAPSHOT_ID, s.WORK_ORDER_ID, s.PROJECT_ID, s.SOURCE_EVENT_ID,
                        s.SOURCE_EVENT_TYPE, s.PRICING_KEY, s.CURRENCY, s.TOTAL_AMOUNT_MINOR,
                        s.MODE, s.CORRELATION_ID, s.CREATED_AT)
                .from(s)
                .where(s.TENANT_ID.eq(tenantId))
                .and(s.WORK_ORDER_ID.eq(workOrderId))
                .orderBy(s.CREATED_AT.desc(), s.PRICING_KEY.asc())
                .fetch(record -> new PricingShadowSnapshotView(
                        record.value1(), record.value2(), record.value3(), record.value4(),
                        record.value5(), record.value6(), record.value7(), record.value8(),
                        record.value9(), record.value10(), record.value11()));
    }
}
