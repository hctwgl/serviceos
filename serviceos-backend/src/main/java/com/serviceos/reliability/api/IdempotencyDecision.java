package com.serviceos.reliability.api;

import java.util.Optional;

/**
 * 幂等判定结果。REPLAY 必须返回首次成功时冻结的资源引用，不可重新执行领域行为。
 */
public record IdempotencyDecision(Kind kind, Optional<String> resourceId) {
    public enum Kind { NEW, REPLAY }

    public static IdempotencyDecision newCommand() {
        return new IdempotencyDecision(Kind.NEW, Optional.empty());
    }

    public static IdempotencyDecision replay(String resourceId) {
        return new IdempotencyDecision(Kind.REPLAY, Optional.of(resourceId));
    }
}
