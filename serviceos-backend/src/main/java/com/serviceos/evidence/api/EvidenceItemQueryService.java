package com.serviceos.evidence.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.List;
import java.util.UUID;

/** EvidenceItem 安全摘要查询边界。 */
public interface EvidenceItemQueryService {
    List<EvidenceItemSummaryView> listSummariesForTask(
            CurrentPrincipal principal, String correlationId, UUID taskId);

    /**
     * M223：Network Portal 作用域；鉴权使用 NETWORK {@code evidence.read}。
     * 未完成可靠解析时返回空列表（不抛冲突），避免污染工作区只读事务。
     */
    List<EvidenceItemSummaryView> listSummariesForTaskOnNetwork(
            CurrentPrincipal principal, String correlationId, UUID taskId, UUID networkId);
}
