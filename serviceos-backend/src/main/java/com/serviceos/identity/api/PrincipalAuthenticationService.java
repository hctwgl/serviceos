package com.serviceos.identity.api;

/** 解析受信外部身份到 ServiceOS 稳定 Principal；未知身份只在显式 JIT 策略允许时登记。 */
public interface PrincipalAuthenticationService {
    String resolveOrRegister(AuthenticatedIdentity identity, String correlationId);
}
