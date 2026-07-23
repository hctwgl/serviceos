package com.serviceos.readmodel.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.AdminResourceDirectoryPage;
import com.serviceos.readmodel.api.AdminResourceDirectoryQueryService;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin 服务网点和师傅页面级目录。 */
@RestController
@RequestMapping("/api/v1/admin")
final class AdminResourceDirectoryController {
    private final AdminResourceDirectoryQueryService queries;
    private final CurrentPrincipalProvider principals;

    AdminResourceDirectoryController(
            AdminResourceDirectoryQueryService queries,
            CurrentPrincipalProvider principals
    ) {
        this.queries = queries;
        this.principals = principals;
    }

    @GetMapping("/resource-directory")
    ResponseEntity<AdminResourceDirectoryPage> load(
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(queries.load(principals.current(), correlationId));
    }
}
