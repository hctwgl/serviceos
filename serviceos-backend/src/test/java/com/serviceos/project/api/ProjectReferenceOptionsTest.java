package com.serviceos.project.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectReferenceOptionsTest {
    @Test
    void copiesAndValidatesOptions() {
        var options = new ProjectReferenceOptions(
                List.of(new ProjectClientOption("client-a", "甲车企", 2)),
                List.of(new ProjectRegionOption("CN-3702", "青岛市", 1)),
                Instant.parse("2026-07-20T00:00:00Z"));
        assertThat(options.clients()).hasSize(1);
        assertThat(options.clients().getFirst().displayName()).isEqualTo("甲车企");
        assertThat(options.regions().getFirst().regionName()).isEqualTo("青岛市");
        assertThatThrownBy(() -> new ProjectClientOption(" ", "x", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProjectRegionOption("CN-3702", "青岛", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
