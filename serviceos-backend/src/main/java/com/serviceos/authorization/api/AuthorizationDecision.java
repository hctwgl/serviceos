package com.serviceos.authorization.api;

import java.util.List;

public record AuthorizationDecision(
        Effect effect,
        List<String> reasonCodes,
        List<String> matchedGrantIds,
        List<String> obligations,
        String policyVersion
) {
    public enum Effect { ALLOW, DENY }

    public static AuthorizationDecision allow() {
        return new AuthorizationDecision(Effect.ALLOW, List.of(), List.of(), List.of(), "unspecified");
    }

    public static AuthorizationDecision deny(String reasonCode) {
        return deny(reasonCode, "unspecified");
    }

    public static AuthorizationDecision deny(String reasonCode, String policyVersion) {
        return new AuthorizationDecision(
                Effect.DENY, List.of(reasonCode), List.of(), List.of(), policyVersion);
    }
}
