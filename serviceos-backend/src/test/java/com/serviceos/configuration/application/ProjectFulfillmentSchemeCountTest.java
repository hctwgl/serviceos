package com.serviceos.configuration.application;

import com.serviceos.configuration.api.ProjectFulfillmentSchemeCount;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectFulfillmentSchemeCountTest {
    @Test
    void rejectsNegativeCounts() {
        UUID projectId = UUID.randomUUID();
        assertThatThrownBy(() -> new ProjectFulfillmentSchemeCount(projectId, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsZeroCounts() {
        UUID projectId = UUID.randomUUID();
        ProjectFulfillmentSchemeCount count = new ProjectFulfillmentSchemeCount(projectId, 0, 2);
        assertThat(count.publishedSchemeCount()).isZero();
        assertThat(count.draftSchemeCount()).isEqualTo(2);
    }
}
