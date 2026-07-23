package com.serviceos.dispatch.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.UUID;

/**
 * Admin 责任网点候选查询。
 *
 * <p>调用方只提供 Task 标识；tenant/project、业务类型、区域和冻结派单策略均从服务端权威事实解析。</p>
 */
public interface NetworkAssignmentCandidateQuery {

    NetworkAssignmentCandidateView findCandidates(
            CurrentPrincipal principal,
            String correlationId,
            UUID taskId
    );
}
