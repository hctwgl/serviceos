package com.serviceos.authorization.api;

/**
 * 字段级最终权限。顺序不是简单继承关系：显式 HIDDEN 永远优先，其余权限由策略合并器决定。
 */
public enum FieldPermission {
    HIDDEN,
    MASKED,
    READ,
    WRITE,
    EXPORT
}
