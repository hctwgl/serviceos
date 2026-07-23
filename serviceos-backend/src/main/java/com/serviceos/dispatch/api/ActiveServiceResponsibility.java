package com.serviceos.dispatch.api;

import java.util.UUID;

/**
 * 某 Task 当前已激活的网点与师傅服务责任快照。
 *
 * @param technicianId 师傅档案标识，用于资格、网点成员关系和展示
 * @param technicianPrincipalId 师傅对应的有效登录主体标识；未绑定有效主体时为空，预约与现场命令必须失败关闭
 */
public record ActiveServiceResponsibility(
        UUID taskId,
        String networkId,
        String technicianId,
        String technicianPrincipalId
) {
}
