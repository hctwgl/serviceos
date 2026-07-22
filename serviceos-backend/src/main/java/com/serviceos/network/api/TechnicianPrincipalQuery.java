package com.serviceos.network.api;

import java.util.Optional;

/**
 * 师傅档案与登录主体之间的只读映射。
 *
 * <p>派单责任保存师傅档案标识，用于网点成员、资格和产能判断；Task、预约与现场作业保存或校验
 * OIDC 登录主体标识。调用方必须通过本端口完成显式转换，禁止把两种标识当成同一个值。</p>
 */
public interface TechnicianPrincipalQuery {
    Optional<String> findActivePrincipalId(String tenantId, String technicianProfileId);
}
