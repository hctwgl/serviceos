package com.serviceos.authorization.application;

import com.serviceos.authorization.api.MeContextView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;

import java.util.UUID;

final class PortalContextSupport {
    private PortalContextSupport() {
    }

    static UUID requirePrincipalUuid(CurrentPrincipal actor) {
        try {
            return UUID.fromString(actor.principalId());
        } catch (IllegalArgumentException ex) {
            throw new BusinessProblem(ProblemCode.ACCESS_DENIED, "当前主体无法形成 Portal 上下文");
        }
    }

    static String adminContextId(String tenantId) {
        return "ADMIN|TENANT|" + tenantId;
    }

    static String networkContextId(UUID networkId) {
        return "NETWORK|NETWORK|" + networkId;
    }

    static String technicianContextId(UUID networkId) {
        return "TECHNICIAN|NETWORK|" + networkId;
    }

    static void requireMatchingVersion(String expected, String actual) {
        if (expected == null || expected.isBlank()) {
            return;
        }
        if (!expected.equals(actual)) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "上下文版本已失效，请刷新后重试");
        }
    }

    static MeContextView requireContext(java.util.List<MeContextView> contexts, String contextId) {
        return contexts.stream()
                .filter(context -> context.contextId().equals(contextId))
                .findFirst()
                .orElseThrow(() -> new BusinessProblem(ProblemCode.ACCESS_DENIED,
                        "请求的 Portal 上下文无效或未授权"));
    }
}
