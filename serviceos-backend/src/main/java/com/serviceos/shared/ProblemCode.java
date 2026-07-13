package com.serviceos.shared;

/**
 * 首条纵向切片使用的稳定错误码。HTTP 文案允许演进，客户端只能依赖这里的错误码语义。
 */
public enum ProblemCode {
    VALIDATION_FAILED,
    IDEMPOTENCY_KEY_REUSED,
    IDEMPOTENCY_IN_PROGRESS,
    PROJECT_CODE_ALREADY_EXISTS,
    RESOURCE_NOT_FOUND,
    INTERNAL_ERROR
}
