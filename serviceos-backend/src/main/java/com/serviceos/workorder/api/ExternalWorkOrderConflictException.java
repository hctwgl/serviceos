package com.serviceos.workorder.api;

/** 同一外部业务键被不同载荷或不同配置包重放。 */
public final class ExternalWorkOrderConflictException extends RuntimeException {
    public ExternalWorkOrderConflictException(String message) {
        super(message);
    }
}
