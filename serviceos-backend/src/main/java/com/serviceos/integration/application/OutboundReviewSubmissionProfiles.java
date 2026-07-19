package com.serviceos.integration.application;

import com.serviceos.integration.spi.OutboundReviewSubmissionProfile;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/** 出站提审 Profile 注册表：按入站 lineage 或 connectorVersion 唯一解析。 */
@Component
public class OutboundReviewSubmissionProfiles {
    private final List<OutboundReviewSubmissionProfile> profiles;

    public OutboundReviewSubmissionProfiles(List<OutboundReviewSubmissionProfile> profiles) {
        this.profiles = List.copyOf(Objects.requireNonNull(profiles, "profiles"));
    }

    public OutboundReviewSubmissionProfile requireForInboundLineage(
            String inboundConnectorVersionId,
            String inboundMessageType
    ) {
        List<OutboundReviewSubmissionProfile> matches = profiles.stream()
                .filter(profile -> profile.supportsInboundLineage(inboundConnectorVersionId, inboundMessageType))
                .toList();
        if (matches.isEmpty()) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND,
                    "No outbound review submission profile for inbound lineage");
        }
        if (matches.size() > 1) {
            throw new BusinessProblem(ProblemCode.INTERNAL_ERROR,
                    "Multiple outbound review submission profiles matched inbound lineage");
        }
        return matches.getFirst();
    }

    public OutboundReviewSubmissionProfile requireByConnectorVersion(String connectorVersionId) {
        List<OutboundReviewSubmissionProfile> matches = profiles.stream()
                .filter(profile -> profile.identity().connectorVersionId().equals(connectorVersionId))
                .toList();
        if (matches.isEmpty()) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND,
                    "No outbound review submission profile for connectorVersionId");
        }
        if (matches.size() > 1) {
            throw new BusinessProblem(ProblemCode.INTERNAL_ERROR,
                    "Multiple outbound review submission profiles share connectorVersionId");
        }
        return matches.getFirst();
    }

    public List<OutboundReviewSubmissionProfile> all() {
        return profiles;
    }
}
