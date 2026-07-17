package com.serviceos.authorization.api;

import java.util.List;

/**
 * 授权解释摘要。只返回匹配 grant/policy/obligation 脱敏信息，不泄露他人敏感授权。
 */
public record AuthorizationExplainResult(
        String effect,
        List<String> reasonCodes,
        List<String> matchedGrantIds,
        List<String> dataScopeExplanations,
        List<String> obligations,
        String policyVersion
) {
}
