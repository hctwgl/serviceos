package com.serviceos.identity.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 首次登录登记默认关闭。只有显式配置的 tenant/client 组合可以 JIT 创建 USER 主体；
 * issuer 与 audience 已由 Spring Resource Server 校验，SERVICE 主体必须预配 IdentityLink。
 */
@Component
final class JitRegistrationPolicy {
    private final Set<String> allowedContexts;

    JitRegistrationPolicy(
            @Value("${serviceos.identity.jit-registration.allowed-contexts:}") String configuredContexts
    ) {
        allowedContexts = Arrays.stream(configuredContexts.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    boolean allows(String tenantId, String clientId) {
        return allowedContexts.contains(tenantId + "|" + clientId);
    }
}
