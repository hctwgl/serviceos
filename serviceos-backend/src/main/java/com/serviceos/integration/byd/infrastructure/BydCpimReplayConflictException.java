package com.serviceos.integration.byd.infrastructure;

/** 同一 APP_KEY、Nonce、Cur_Time 被不同请求体复用。 */
public final class BydCpimReplayConflictException extends RuntimeException {
    public BydCpimReplayConflictException() {
        super("BYD CPIM nonce was reused with a different payload");
    }
}
