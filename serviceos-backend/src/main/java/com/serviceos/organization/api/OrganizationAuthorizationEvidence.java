package com.serviceos.organization.api;

import java.util.List;

public record OrganizationAuthorizationEvidence(List<String> matchedGrantIds, String policyVersion) {
}
