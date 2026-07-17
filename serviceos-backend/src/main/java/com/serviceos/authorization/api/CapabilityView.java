package com.serviceos.authorization.api;

/** 稳定 Capability 目录项；风险级别不可由租户改写。 */
public record CapabilityView(
        String capabilityCode,
        String capabilityName,
        String riskLevel
) {
}
