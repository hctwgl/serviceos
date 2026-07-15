package com.serviceos.integration.byd.application;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BydCpimReviewCallbackMapperTest {
    private final BydCpimReviewCallbackMapper mapper = new BydCpimReviewCallbackMapper();

    @Test
    void mapsStrictBatchAndRejectionSemantics() {
        var mapped = mapper.map(Map.of(
                "orderCode", "ORDER-1,ORDER-2",
                "result", "2",
                "remark", "资料不完整",
                "examinePerson", "审核员",
                "examineDate", "2026-07-15 09:30:00"));

        assertThat(mapped.orderCodes()).containsExactly("ORDER-1", "ORDER-2");
        assertThat(mapped.domainResult()).isEqualTo("REJECTED");
    }

    @Test
    void rejectsUnknownDuplicateLooseDateAndMissingRejectRemark() {
        assertThatThrownBy(() -> mapper.map(payload("ORDER-1,ORDER-1", "1", "ok",
                "2026-07-15 09:30:00"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mapper.map(payload("ORDER-1", "2", null,
                "2026-07-15 09:30:00"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mapper.map(payload("ORDER-1", "1", "ok",
                "2026-02-30 09:30:00"))).isInstanceOf(IllegalArgumentException.class);

        Map<String, Object> unknown = payload("ORDER-1", "1", "ok", "2026-07-15 09:30:00");
        unknown.put("unexpected", "value");
        assertThatThrownBy(() -> mapper.map(unknown)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Map<String, Object> payload(
            String orderCode, String result, String remark, String examineDate
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderCode", orderCode);
        payload.put("result", result);
        payload.put("remark", remark);
        payload.put("examinePerson", "审核员");
        payload.put("examineDate", examineDate);
        return payload;
    }
}
