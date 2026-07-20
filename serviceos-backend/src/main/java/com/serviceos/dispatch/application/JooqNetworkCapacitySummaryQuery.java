package com.serviceos.dispatch.application;

import com.serviceos.dispatch.api.NetworkCapacityCounterView;
import com.serviceos.dispatch.api.NetworkCapacitySummaryQuery;
import com.serviceos.jooq.generated.tables.DspCapacityCounter;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.serviceos.jooq.generated.tables.DspCapacityCounter.DSP_CAPACITY_COUNTER;

/** 网点容量计数器只读适配：按 NETWORK assignee_id = networkId 查询。 */
@Service
final class JooqNetworkCapacitySummaryQuery implements NetworkCapacitySummaryQuery {
    private final DSLContext dsl;

    JooqNetworkCapacitySummaryQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NetworkCapacityCounterView> listForNetwork(String tenantId, String networkId) {
        DspCapacityCounter counter = DSP_CAPACITY_COUNTER;
        return dsl.select(
                        counter.CAPACITY_COUNTER_ID,
                        counter.BUSINESS_TYPE,
                        counter.MAX_UNITS,
                        counter.OCCUPIED_UNITS,
                        counter.VERSION,
                        counter.UPDATED_AT)
                .from(counter)
                .where(counter.TENANT_ID.eq(tenantId))
                .and(counter.RESPONSIBILITY_LEVEL.eq("NETWORK"))
                .and(counter.ASSIGNEE_ID.eq(networkId))
                .orderBy(counter.BUSINESS_TYPE, counter.CAPACITY_COUNTER_ID)
                .fetch(row -> new NetworkCapacityCounterView(
                        row.value1(), row.value2(), row.value3(), row.value4(), row.value5(), row.value6()));
    }
}
