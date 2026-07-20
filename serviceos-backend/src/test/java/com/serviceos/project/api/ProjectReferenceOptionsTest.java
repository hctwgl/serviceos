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
                List.of(new ProjectClientOption("client-a", 2)),
                List.of(new ProjectRegionOption("CN-3702", 1)),
                Instant.parse("2026-07-20T00:00:00Z"));
        assertThat(options.clients()).hasSize(1);
        assertThat(options.regions().getFirst().regionCode()).isEqualTo("CN-3702");
        assertThatThrownBy(() -> new ProjectClientOption(" ", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProjectRegionOption("CN-3702", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
