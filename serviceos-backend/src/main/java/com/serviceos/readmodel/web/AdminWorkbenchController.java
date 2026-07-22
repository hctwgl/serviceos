package com.serviceos.readmodel.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.AdminWorkbenchQueryService;
import com.serviceos.readmodel.api.AdminWorkbenchView;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin 产品首页页面级查询入口。 */
@RestController
@RequestMapping("/api/v1/admin/workbench")
final class AdminWorkbenchController {
    private final AdminWorkbenchQueryService workbench;
    private final CurrentPrincipalProvider principals;

    AdminWorkbenchController(
            AdminWorkbenchQueryService workbench,
            CurrentPrincipalProvider principals
    ) {
        this.workbench = workbench;
        this.principals = principals;
    }

    @GetMapping
    ResponseEntity<AdminWorkbenchView> get(
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(workbench.get(principals.current(), correlationId));
    }
}
