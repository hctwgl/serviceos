package com.serviceos.authorization.application;

import java.util.List;

/**
 * 当前时刻命中的 ServiceOS 权威 RoleGrant；JWT 声明不进入 matchedGrantIds。
 */
public record CapabilityGrantMatch(boolean allowed, List<String> matchedGrantIds, String policyVersion) {
    public static CapabilityGrantMatch denied(String policyVersion) {
        return new CapabilityGrantMatch(false, List.of(), policyVersion);
    }
}
