package com.serviceos.network.application;

import java.util.function.Supplier;

/**
 * M204：在 Network Portal 委托师傅目录命令期间，将底层 network.* 鉴权收窄为 NETWORK scope。
 * <p>
 * 为什么用请求线程局部值：避免扩大 NetworkCommandService 公共 API 签名，同时保证
 * TENANT 级 Admin 目录写路径不变。调用方必须在 finally 清理，防止线程复用泄漏。
 */
final class NetworkScopedNetworkAuthorization {
    private static final ThreadLocal<String> NETWORK_SCOPE = new ThreadLocal<>();

    private NetworkScopedNetworkAuthorization() {
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
