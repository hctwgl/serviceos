package com.serviceos.task.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CorrelationIds;
import com.serviceos.task.api.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/tasks")
final class TaskDirectoryController {
 private final TaskDirectoryQueryService queries;private final CurrentPrincipalProvider principals;
 TaskDirectoryController(TaskDirectoryQueryService queries,CurrentPrincipalProvider principals){this.queries=queries;this.principals=principals;}
 @GetMapping ResponseEntity<TaskDirectoryPage> list(@RequestParam(required=false) UUID projectId,@RequestParam(required=false) String taskKind,@RequestParam(required=false) String status,@RequestParam(required=false) String assignee,@RequestParam(required=false) String cursor,@RequestParam(defaultValue="50") int limit,@RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId){return ResponseEntity.ok().header(CorrelationIds.HEADER_NAME,correlationId).body(queries.list(principals.current(),correlationId,new TaskDirectoryQuery(projectId,taskKind,status,assignee,cursor,limit)));}
 @GetMapping("/{taskId}") ResponseEntity<TaskDetail> get(@PathVariable UUID taskId,@RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId){TaskDetail d=queries.get(principals.current(),correlationId,taskId);return ResponseEntity.ok().eTag(Long.toString(d.task().version())).header(CorrelationIds.HEADER_NAME,correlationId).body(d);}
}
