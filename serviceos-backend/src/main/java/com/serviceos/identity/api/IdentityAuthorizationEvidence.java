package com.serviceos.identity.api;

import java.util.List;

/** identity 高风险操作通过实时 RoleGrant 校验后返回的最小审计证据。 */
public record IdentityAuthorizationEvidence(List<String> matchedGrantIds, String policyVersion) {
    public IdentityAuthorizationEvidence {
        matchedGrantIds = matchedGrantIds == null ? List.of() : List.copyOf(matchedGrantIds);
    }
}
