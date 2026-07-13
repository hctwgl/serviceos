package com.serviceos.integration.byd.infrastructure;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BydCpimPayloadDigestTest {
    @Test
    void digestIsIndependentFromInputMapOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("orderCode", "BYD-001");
        first.put("carBrand", "40");

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("carBrand", "40");
        second.put("orderCode", "BYD-001");

        assertThat(BydCpimPayloadDigest.sha256(first))
                .isEqualTo(BydCpimPayloadDigest.sha256(second))
                .hasSize(64);
    }

    @Test
    void changedValueProducesDifferentDigest() {
        assertThat(BydCpimPayloadDigest.sha256(Map.of("orderCode", "BYD-001")))
                .isNotEqualTo(BydCpimPayloadDigest.sha256(Map.of("orderCode", "BYD-002")));
    }

    @Test
    void nestedValuesAreRejectedUntilProtocolSpecificCanonicalizationExists() {
        assertThatThrownBy(() -> BydCpimPayloadDigest.sha256(Map.of("payload", Map.of("a", 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nested parameter");
    }
}
