package com.serviceos.dispatch.api;

import java.util.List;

/**
 * 按网点列出 ACTIVE NETWORK ServiceAssignment。
 * 无 HTTP 暴露；供 Network Portal 等已鉴权编排使用。
 */
public interface NetworkActiveAssignmentQuery {
    List<NetworkActiveAssignmentView> listActiveForNetwork(String tenantId, String networkId);
}
