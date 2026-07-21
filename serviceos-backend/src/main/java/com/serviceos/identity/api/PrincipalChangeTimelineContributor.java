package com.serviceos.identity.api;

import java.util.List;
import java.util.UUID;

/**
 * 主体变更时间线跨模块贡献点。
 *
 * <p>由 organization / authorization 等模块实现；identity 只按 soft-gate 能力合并结果，
 * 避免 identity 反向依赖业务模块内部包。</p>
 */
public interface PrincipalChangeTimelineContributor {
    /** 来源码，如 MEMBERSHIP / ROLE_GRANT / NETWORK_MEMBERSHIP / TECHNICIAN_*。 */
    String source();

    /** soft-gate 所需租户能力；缺权时 identity 记入 omittedSources，不失败关闭整页。 */
    String requiredCapability();

    /**
     * 返回该主体相关的不可变业务事件投影；不得合成无稳定事实 UUID 的行。
     */
    List<PrincipalChangeTimelineItem> listForPrincipal(String tenantId, UUID principalId, int limit);
}
