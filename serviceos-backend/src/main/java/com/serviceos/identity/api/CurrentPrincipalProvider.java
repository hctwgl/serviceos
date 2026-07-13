package com.serviceos.identity.api;

/**
 * 从当前受信安全上下文解析主体；业务 controller 不直接解释 JWT claim。
 */
public interface CurrentPrincipalProvider {
    CurrentPrincipal current();
}
