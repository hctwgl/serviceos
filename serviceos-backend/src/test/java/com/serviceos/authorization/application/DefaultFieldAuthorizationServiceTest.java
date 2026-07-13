package com.serviceos.authorization.application;

import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.authorization.api.FieldAccessDecision;
import com.serviceos.authorization.api.FieldAuthorizationRequest;
import com.serviceos.authorization.api.FieldPermission;
import com.serviceos.identity.api.CurrentPrincipal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultFieldAuthorizationServiceTest {
    private final CurrentPrincipal principal = new CurrentPrincipal(
            "user-1", "tenant-a", CurrentPrincipal.PrincipalType.USER,
            "admin-web", Set.of("workOrder.read"));
    private final AuthorizationRequest authorizationRequest = AuthorizationRequest.projectCapability(
            "workOrder.read", "tenant-a", "WorkOrder", "wo-1", "project-a");

    @Test
    void actionDenialHidesEveryRequestedFieldWithoutCallingPolicyStore() {
        AuthorizationService authorization = fixedAuthorization(AuthorizationDecision.deny(
                "CAPABILITY_MISSING", "role-grant-v2"));
        FieldPolicyStore policies = (tenant, grants, capability, resource, fields) -> {
            throw new AssertionError("field policy must not be queried after action denial");
        };

        var decision = new DefaultFieldAuthorizationService(authorization, policies).evaluate(
                principal,
                new FieldAuthorizationRequest(authorizationRequest, Set.of("customerMobile", "settlementAmount")),
                "corr-deny");

        assertThat(decision.fields()).allSatisfy((field, access) ->
                assertThat(access.permission()).isEqualTo(FieldPermission.HIDDEN));
        assertThat(decision.matchedGrantIds()).isEmpty();
    }

    @Test
    void missingFieldRuleIsHiddenWhilePublishedRulesAreReturned() {
        AuthorizationDecision actionAllowed = new AuthorizationDecision(
                AuthorizationDecision.Effect.ALLOW,
                List.of(), List.of("grant-1"), List.of("PROJECT:project-a"),
                List.of(), "role-grant-v2");
        FieldPolicyStore policies = (tenant, grants, capability, resource, fields) ->
                new FieldPolicyMatch(Map.of(
                        "customerMobile", new FieldAccessDecision(FieldPermission.MASKED, "CN_MOBILE"),
                        "customerName", new FieldAccessDecision(FieldPermission.READ, null)),
                        "field-policy-v1");

        var decision = new DefaultFieldAuthorizationService(fixedAuthorization(actionAllowed), policies).evaluate(
                principal,
                new FieldAuthorizationRequest(
                        authorizationRequest,
                        Set.of("customerMobile", "customerName", "settlementAmount")),
                "corr-fields");

        assertThat(decision.fields().get("customerMobile"))
                .isEqualTo(new FieldAccessDecision(FieldPermission.MASKED, "CN_MOBILE"));
        assertThat(decision.fields().get("customerName").permission()).isEqualTo(FieldPermission.READ);
        assertThat(decision.fields().get("settlementAmount").permission()).isEqualTo(FieldPermission.HIDDEN);
        assertThat(decision.policyVersion()).isEqualTo("role-grant-v2+field-policy-v1");
    }

    private static AuthorizationService fixedAuthorization(AuthorizationDecision decision) {
        return new AuthorizationService() {
            @Override
            public AuthorizationDecision authorize(
                    CurrentPrincipal principal,
                    AuthorizationRequest request,
                    String correlationId
            ) {
                return decision;
            }

            @Override
            public AuthorizationDecision require(
                    CurrentPrincipal principal,
                    AuthorizationRequest request,
                    String correlationId
            ) {
                return decision;
            }
        };
    }
}
