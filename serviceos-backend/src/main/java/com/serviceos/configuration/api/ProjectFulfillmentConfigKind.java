package com.serviceos.configuration.api;

/** 工单履约配置冻结种类。 */
public enum ProjectFulfillmentConfigKind {
    /** 冻结 Profile Revision + Bundle。 */
    PROFILE_REVISION,
    /** 能力上线前仅冻结 Bundle 的历史工单。 */
    LEGACY_BUNDLE
}
