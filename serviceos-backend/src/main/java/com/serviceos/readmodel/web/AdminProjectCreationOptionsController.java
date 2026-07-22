package com.serviceos.readmodel.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.AdminProjectCreationOptionsQueryService;
import com.serviceos.readmodel.api.AdminProjectCreationOptionsView;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin 创建项目页面级选项 HTTP 入口。 */
@RestController
@RequestMapping("/api/v1/admin/projects")
final class AdminProjectCreationOptionsController {
    private final AdminProjectCreationOptionsQueryService queries;
    private final CurrentPrincipalProvider principals;

    AdminProjectCreationOptionsController(
            AdminProjectCreationOptionsQueryService queries,
            CurrentPrincipalProvider principals
    ) {
        this.queries = queries;
        this.principals = principals;
    }

    @GetMapping("/creation-options")
    ResponseEntity<AdminProjectCreationOptionsView> load(
            @RequestParam(required = false) String regionQuery,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(queries.load(principals.current(), correlationId, regionQuery));
    }
}
