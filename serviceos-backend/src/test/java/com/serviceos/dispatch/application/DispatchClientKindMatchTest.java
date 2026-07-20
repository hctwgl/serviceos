package com.serviceos.dispatch.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DispatchClientKindMatchTest {
    @Test
    void undeclaredFailsWhenFilterApplies() {
        assertThat(DispatchClientKindCompatibility.matchesDeclaredClientKinds(
                null, List.of("TECHNICIAN_WEB"))).isFalse();
        assertThat(DispatchClientKindCompatibility.matchesDeclaredClientKinds(
                List.of(), List.of("TECHNICIAN_WEB"))).isFalse();
    }

    @Test
    void matchingKindPasses() {
        assertThat(DispatchClientKindCompatibility.matchesDeclaredClientKinds(
                List.of("TECHNICIAN_IOS", "TECHNICIAN_WEB"), List.of("TECHNICIAN_WEB")))
                .isTrue();
    }

    @Test
    void disjointKindsFail() {
        assertThat(DispatchClientKindCompatibility.matchesDeclaredClientKinds(
                List.of("TECHNICIAN_IOS"), List.of("TECHNICIAN_WEB")))
                .isFalse();
    }

    @Test
    void emptyTargetFailsClosed() {
        assertThat(DispatchClientKindCompatibility.matchesDeclaredClientKinds(
                List.of("TECHNICIAN_WEB"), List.of()))
                .isFalse();
    }
}
