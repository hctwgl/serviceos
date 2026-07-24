package com.serviceos.dispatch.application;

import com.serviceos.dispatch.api.ActiveServiceResponsibility;
import com.serviceos.dispatch.api.ActiveServiceResponsibilityService;
import com.serviceos.network.api.TechnicianPrincipalQuery;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
final class DefaultActiveServiceResponsibilityService implements ActiveServiceResponsibilityService {
    private final ActiveServiceResponsibilityRepository repository;
    private final TechnicianPrincipalQuery technicianPrincipals;
    private final TaskFulfillmentContextService tasks;

    DefaultActiveServiceResponsibilityService(
            ActiveServiceResponsibilityRepository repository,
            TechnicianPrincipalQuery technicianPrincipals,
            TaskFulfillmentContextService tasks
    ) {
        this.repository = repository;
        this.technicianPrincipals = technicianPrincipals;
        this.tasks = tasks;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ActiveServiceResponsibility> find(String tenantId, UUID taskId) {
        return tasks.find(tenantId, taskId)
                .map(TaskFulfillmentContext::workOrderId)
                .flatMap(workOrderId -> repository.find(tenantId, taskId, workOrderId))
                .map(responsibility -> enrich(tenantId, responsibility));
    }

    private ActiveServiceResponsibility enrich(
            String tenantId, ActiveServiceResponsibility responsibility
    ) {
        if (responsibility.technicianId() == null) {
            return responsibility;
        }
        // 责任查询同时服务工单摘要、网点队列等只读场景。师傅档案即使暂未绑定有效登录主体，
        // 这些页面仍应能够展示并暴露数据问题；真正代表师傅执行预约或现场命令时，由调用方
        // 使用 technicianPrincipalId 做失败关闭校验，避免把主数据缺陷扩大成整个工作区不可用。
        String principalId = technicianPrincipals.findActivePrincipalId(
                        tenantId, responsibility.technicianId())
                .orElse(null);
        return new ActiveServiceResponsibility(
                responsibility.taskId(), responsibility.networkId(),
                responsibility.technicianId(), principalId);
    }
}
