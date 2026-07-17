package com.serviceos.readmodel.api;

/** SavedView 可见性。TENANT 为租户级共享；ORGANIZATION 组织树共享本切片不接受。 */
public enum SavedViewVisibility {
    PRIVATE,
    ROLE,
    TENANT
}
