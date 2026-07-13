package com.serviceos.authorization.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultAuthorizationServiceTest {
    private final List<AuditEntry> entries = new ArrayList<>();
    private final AuditAppender audit = entries::add;
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-13T03:30:00Z"), ZoneOffset.UTC);
    private final AuthorizationDenialAuditWriter writer = new AuthorizationDenialAuditWriter(audit, clock);
    private CapabilityGrantMatch grantMatch = new CapabilityGrantMatch(
            true, List.of("grant-1"), "test-policy-v1");
    private final AuthorizationPolicyStore policyStore = (tenant, principal, capability, evaluatedAt) -> grantMatch;
    private final DefaultAuthorizationService service = new DefaultAuthorizationService(writer, policyStore, clock);

    @Test
    void matchingTenantAndCapabilityAreAllowed() {
        AuthorizationDecision decision = service.authorize(
                principal(Set.of("project.create")), request("tenant-a"), "corr-1");

        assertThat(decision.effect()).isEqualTo(AuthorizationDecision.Effect.ALLOW);
        assertThat(entries).isEmpty();
    }

    @Test
    void missingCapabilityIsDeniedAndAudited() {
        grantMatch = CapabilityGrantMatch.denied("test-policy-v1");
        assertThatThrownBy(() -> service.require(
                principal(Set.of("workOrder.read")), request("tenant-a"), "corr-2"))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));

        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.decision()).isEqualTo("DENY");
            assertThat(entry.errorCode()).isEqualTo(DefaultAuthorizationService.CAPABILITY_MISSING);
            assertThat(entry.capabilityCode()).isEqualTo("project.create");
        });
    }

    @Test
    void crossTenantRequestIsDeniedBeforeCapabilityCanGrantAccess() {
        AuthorizationDecision decision = service.authorize(
                principal(Set.of("project.create")), request("tenant-b"), "corr-3");

        assertThat(decision.reasonCodes())
                .containsExactly(DefaultAuthorizationService.TENANT_SCOPE_MISMATCH);
        assertThat(decision.policyVersion()).isEqualTo("tenant-boundary-v1");
    }

    @Test
    void signedTokenCapabilityCannotReplaceRevokedOrMissingRoleGrant() {
        grantMatch = CapabilityGrantMatch.denied("test-policy-v1");

        AuthorizationDecision decision = service.authorize(
                principal(Set.of("project.create")), request("tenant-a"), "corr-4");

        assertThat(decision.effect()).isEqualTo(AuthorizationDecision.Effect.DENY);
        assertThat(decision.reasonCodes()).containsExactly(DefaultAuthorizationService.CAPABILITY_MISSING);
    }

    private static CurrentPrincipal principal(Set<String> capabilities) {
        return new CurrentPrincipal(
                "user-1", "tenant-a", CurrentPrincipal.PrincipalType.USER,
                "admin-web", capabilities);
    }

    private static AuthorizationRequest request(String tenantId) {
        return AuthorizationRequest.tenantCapability("project.create", tenantId, "Project", "BYD-2026");
    }
}
