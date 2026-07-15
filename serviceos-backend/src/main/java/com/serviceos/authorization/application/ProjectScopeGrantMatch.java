package com.serviceos.authorization.application;

import java.util.List;

/** 当前时刻命中的项目集合 RoleGrant 原始范围。 */
public record ProjectScopeGrantMatch(List<String> scopeTypesAndRefs, String policyVersion) {
    public ProjectScopeGrantMatch {
        scopeTypesAndRefs = List.copyOf(scopeTypesAndRefs);
    }
}
