package com.serviceos.identity.infrastructure;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * JWT 到领域主体的唯一映射点，避免各 controller 对 claim 名称产生不同解释。
 */
@Component
final class SecurityContextCurrentPrincipalProvider implements CurrentPrincipalProvider {
    @Override
    public CurrentPrincipal current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new BusinessProblem(ProblemCode.UNAUTHENTICATED, "Authentication is required");
        }

        String tenantId = jwt.getClaimAsString("tenant_id");
        if (tenantId == null || tenantId.isBlank()) {
            throw new BusinessProblem(ProblemCode.UNAUTHENTICATED, "The authenticated subject has no tenant context");
        }

        Set<String> capabilities = new LinkedHashSet<>();
        List<String> explicitCapabilities = jwt.getClaimAsStringList("capabilities");
        if (explicitCapabilities != null) {
            capabilities.addAll(explicitCapabilities);
        }
        String scope = jwt.getClaimAsString("scope");
        if (scope != null && !scope.isBlank()) {
            capabilities.addAll(List.of(scope.trim().split("\\s+")));
        }

        String principalTypeClaim = jwt.getClaimAsString("principal_type");
        CurrentPrincipal.PrincipalType principalType = "service".equalsIgnoreCase(principalTypeClaim)
                ? CurrentPrincipal.PrincipalType.SERVICE
                : CurrentPrincipal.PrincipalType.USER;
        String clientId = jwt.hasClaim("client_id")
                ? jwt.getClaimAsString("client_id")
                : jwt.getClaimAsString("azp");

        return new CurrentPrincipal(jwt.getSubject(), tenantId, principalType, clientId, capabilities);
    }
}
