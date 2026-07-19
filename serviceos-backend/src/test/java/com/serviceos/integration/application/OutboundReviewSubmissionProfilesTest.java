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

    @Test
    void resolvesUniqueProfileByCallbackMappingVersion() {
        var byd = new BydOutboundReviewSubmissionProfile(JsonMapper.builder().build());
        var registry = new OutboundReviewSubmissionProfiles(List.of(byd));
        assertThat(registry.requireByCallbackMappingVersion("byd-ocean-shandong-review-callback-v1"))
                .isSameAs(byd);
        assertThatThrownBy(() -> registry.requireByCallbackMappingVersion("unknown-mapping"))
                .isInstanceOf(BusinessProblem.class)
                .extracting(ex -> ((BusinessProblem) ex).code())
                .isEqualTo(ProblemCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void routeRegistrationFallsBackToSoleProfileWhenMappingUnknown() {
        var byd = new BydOutboundReviewSubmissionProfile(JsonMapper.builder().build());
        var registry = new OutboundReviewSubmissionProfiles(List.of(byd));
        assertThat(registry.requireForRouteRegistration("MAP-TEST-ONLY")).isSameAs(byd);
    }

    @Test
    void routeRegistrationFailsClosedWhenMappingUnknownAndMultipleProfiles() {
        var byd = new BydOutboundReviewSubmissionProfile(JsonMapper.builder().build());
        var geely = new com.serviceos.integration.geely.application.GeelyOutboundReviewSubmissionProfile(
                JsonMapper.builder().build());
        var registry = new OutboundReviewSubmissionProfiles(List.of(byd, geely));
        assertThat(registry.requireForRouteRegistration("byd-ocean-shandong-review-callback-v1"))
                .isSameAs(byd);
        assertThat(registry.requireForRouteRegistration("geely-haohan-v1.3-settlement-audit-callback-v1"))
                .isSameAs(geely);
        assertThatThrownBy(() -> registry.requireForRouteRegistration("MAP-AMBIGUOUS"))
                .isInstanceOf(BusinessProblem.class)
                .extracting(ex -> ((BusinessProblem) ex).code())
                .isEqualTo(ProblemCode.RESOURCE_NOT_FOUND);
    }
}
