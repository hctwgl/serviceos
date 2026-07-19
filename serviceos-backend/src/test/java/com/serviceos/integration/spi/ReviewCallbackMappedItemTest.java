package com.serviceos.integration.spi;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewCallbackMappedItemTest {

    @Test
    void acceptsApprovedAndRejectedDomainResults() {
        ReviewCallbackMappedItem approved = new ReviewCallbackMappedItem(
                "ORDER-1", "BYD:REVIEW:ORDER-1:1:2026-07-19 10:00:00", "ORDER-1",
                "APPROVED", List.of(), "mapping-v1", "{\"ok\":true}".getBytes());
        ReviewCallbackMappedItem rejected = new ReviewCallbackMappedItem(
                "ORDER-2", "BYD:REVIEW:ORDER-2:2:2026-07-19 10:00:00", "ORDER-2",
                "REJECTED", List.of("BYD.REVIEW.REJECTED"), "mapping-v1", "{\"ok\":false}".getBytes());
        assertThat(approved.domainResult()).isEqualTo("APPROVED");
        assertThat(rejected.reasonCodes()).containsExactly("BYD.REVIEW.REJECTED");
        assertThat(ReviewCallbackMappedItem.MESSAGE_TYPE_RECORD_CLIENT_REVIEW_RESULT)
                .isEqualTo("RECORD_CLIENT_REVIEW_RESULT");
    }

    @Test
    void rejectsUnknownDomainResult() {
        assertThatThrownBy(() -> new ReviewCallbackMappedItem(
                "ORDER-1", "bk", "ORDER-1", "PASS", List.of(), "mapping-v1", "{}".getBytes()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("domainResult");
    }
}
