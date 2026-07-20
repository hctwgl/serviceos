package com.serviceos.configuration.api;

/** 履约 Profile 生命周期；与某一 Revision 状态分离。 */
public enum ProjectFulfillmentProfileStatus {
    DRAFT,
    ACTIVE,
    SUSPENDED,
    RETIRED
}
