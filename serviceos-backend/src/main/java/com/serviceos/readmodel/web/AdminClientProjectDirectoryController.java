package com.serviceos.readmodel.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.AdminClientProjectDirectoryQueryService;
import com.serviceos.readmodel.api.AdminClientProjectDirectoryView;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin 客户与项目页面级目录 HTTP 入口。 */
@RestController
@RequestMapping("/api/v1/admin")
final class AdminClientProjectDirectoryController {
    private final AdminClientProjectDirectoryQueryService queries;
    private final CurrentPrincipalProvider principals;

    AdminClientProjectDirectoryController(
            AdminClientProjectDirectoryQueryService queries,
            CurrentPrincipalProvider principals
    ) {
        this.queries = queries;
        this.principals = principals;
    }

    @GetMapping("/client-project-directory")
    ResponseEntity<AdminClientProjectDirectoryView> load(
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(queries.load(principals.current(), correlationId));
    }
}
