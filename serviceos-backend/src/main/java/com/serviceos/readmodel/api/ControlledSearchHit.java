package com.serviceos.readmodel.api;

/**
 * 受控搜索命中项。字段最小化；maskedSecondaryLabel 不得承载完整敏感 query。
 */
public record ControlledSearchHit(
        String resourceRef,
        ControlledSearchType type,
        String primaryLabel,
        String maskedSecondaryLabel,
        String matchReason,
        String deepLink
) {}
