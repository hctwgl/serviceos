package com.serviceos.integration.application;

import com.serviceos.integration.byd.application.BydOutboundReviewSubmissionProfile;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboundReviewSubmissionProfilesTest {

    @Test
    void resolvesUniqueBydProfileByInboundLineageAndConnectorVersion() {
        var byd = new BydOutboundReviewSubmissionProfile(JsonMapper.builder().build());
        var registry = new OutboundReviewSubmissionProfiles(List.of(byd));
        assertThat(registry.requireForInboundLineage("byd-cpim-v7.3.1", "CREATE_WORK_ORDER"))
                .isSameAs(byd);
        assertThat(registry.requireByConnectorVersion("byd-cpim-v7.3.1").taskType())
                .isEqualTo("integration.byd.submit-review");
    }

    @Test
    void failsClosedWhenLineageUnknown() {
        var registry = new OutboundReviewSubmissionProfiles(List.of(
                new BydOutboundReviewSubmissionProfile(JsonMapper.builder().build())));
        assertThatThrownBy(() -> registry.requireForInboundLineage("unknown-connector", "CREATE_WORK_ORDER"))
                .isInstanceOf(BusinessProblem.class)
                .extracting(ex -> ((BusinessProblem) ex).code())
                .isEqualTo(ProblemCode.RESOURCE_NOT_FOUND);
    }
}
