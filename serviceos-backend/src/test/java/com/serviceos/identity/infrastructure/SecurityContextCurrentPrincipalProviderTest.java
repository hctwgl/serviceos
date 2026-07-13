package com.serviceos.identity.infrastructure;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityContextCurrentPrincipalProviderTest {
    private final SecurityContextCurrentPrincipalProvider provider = new SecurityContextCurrentPrincipalProvider();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void mapsTrustedJwtClaimsIntoStablePrincipal() {
        Jwt jwt = jwtBuilder()
                .subject("user-1001")
                .claim("tenant_id", "tenant-a")
                .claim("client_id", "admin-web")
                .claim("capabilities", List.of("project.create", "workOrder.read"))
                .claim("scope", "profile evidence.submit")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_TEST"))));

        CurrentPrincipal principal = provider.current();

        assertThat(principal.principalId()).isEqualTo("user-1001");
        assertThat(principal.tenantId()).isEqualTo("tenant-a");
        assertThat(principal.assertedCapabilities())
                .contains("project.create", "workOrder.read", "profile", "evidence.submit");
    }

    @Test
    void tokenWithoutTenantContextIsRejected() {
        Jwt jwt = jwtBuilder().subject("user-1001").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_TEST"))));

        assertThatThrownBy(provider::current)
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.UNAUTHENTICATED));
    }

    private static Jwt.Builder jwtBuilder() {
        Instant now = Instant.parse("2026-07-13T03:30:00Z");
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300));
    }
}
