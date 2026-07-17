package com.serviceos.dispatch.application;

import java.util.function.Supplier;

/**
 * M196：在 Network Portal 委托 ManualAssign 期间，将底层派单/容量鉴权收窄为 NETWORK scope。
 * <p>
 * 为什么用请求线程局部值：避免扩大 ServiceAssignment/Capacity 公共 API 签名，同时保证
 * TENANT 级 Admin ManualAssign 路径不变。调用方必须在 finally 清理，防止线程复用泄漏。
 */
final class NetworkScopedDispatchAuthorization {
    private static final ThreadLocal<String> NETWORK_SCOPE = new ThreadLocal<>();

    private NetworkScopedDispatchAuthorization() {
    }

    static <T> T callWith(String networkId, Supplier<T> action) {
        NETWORK_SCOPE.set(networkId);
        try {
            return action.get();
        } finally {
            NETWORK_SCOPE.remove();
        }
    }

    static String currentNetworkId() {
        return NETWORK_SCOPE.get();
    }
}
