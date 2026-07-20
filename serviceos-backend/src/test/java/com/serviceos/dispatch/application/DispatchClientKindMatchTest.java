package com.serviceos.dispatch.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DispatchClientKindMatchTest {
    @Test
    void undeclaredFailsWhenFilterApplies() {
        assertThat(DefaultTaskDispatchPolicyEventConsumer.matchesDeclaredClientKinds(
                null, List.of("TECHNICIAN_WEB"))).isFalse();
        assertThat(DefaultTaskDispatchPolicyEventConsumer.matchesDeclaredClientKinds(
                List.of(), List.of("TECHNICIAN_WEB"))).isFalse();
    }

    @Test
    void matchingKindPasses() {
        assertThat(DefaultTaskDispatchPolicyEventConsumer.matchesDeclaredClientKinds(
                List.of("TECHNICIAN_IOS", "TECHNICIAN_WEB"), List.of("TECHNICIAN_WEB")))
                .isTrue();
    }

    @Test
    void disjointKindsFail() {
        assertThat(DefaultTaskDispatchPolicyEventConsumer.matchesDeclaredClientKinds(
                List.of("TECHNICIAN_IOS"), List.of("TECHNICIAN_WEB")))
                .isFalse();
    }

    @Test
    void emptyTargetFailsClosed() {
        assertThat(DefaultTaskDispatchPolicyEventConsumer.matchesDeclaredClientKinds(
                List.of("TECHNICIAN_WEB"), List.of()))
                .isFalse();
    }
}
