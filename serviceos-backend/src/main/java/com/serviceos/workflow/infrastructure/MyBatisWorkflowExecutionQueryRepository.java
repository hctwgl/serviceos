package com.serviceos.workflow.infrastructure;

import com.serviceos.workflow.api.*;
import com.serviceos.workflow.application.WorkflowExecutionQueryRepository;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;

@Repository
final class MyBatisWorkflowExecutionQueryRepository implements WorkflowExecutionQueryRepository {
 private final WorkflowExecutionQueryMapper mapper;
 MyBatisWorkflowExecutionQueryRepository(WorkflowExecutionQueryMapper mapper){this.mapper=mapper;}
 public Optional<WorkflowInstanceView> findWorkflow(String tenant,UUID workOrder){return Optional.ofNullable(mapper.findWorkflow(tenant,workOrder)).map(MyBatisWorkflowExecutionQueryRepository::workflow);}
 public List<StageInstanceView> findStages(String tenant,UUID workOrder){return mapper.findStages(tenant,workOrder).stream().map(MyBatisWorkflowExecutionQueryRepository::stage).toList();}
 private static WorkflowInstanceView workflow(Map<String,Object> r){return new WorkflowInstanceView(u(r,"id"),u(r,"projectId"),u(r,"workOrderId"),u(r,"configurationBundleId"),u(r,"workflowDefinitionVersionId"),s(r,"workflowKey"),s(r,"workflowVersion"),s(r,"definitionDigest"),s(r,"status"),s(r,"currentPhaseCode"),null,s(r,"currentNodeCode"),null,null,n(r,"version"),i(r,"startedAt"),i(r,"completedAt"));}
 private static StageInstanceView stage(Map<String,Object> r){return new StageInstanceView(u(r,"id"),u(r,"workflowInstanceId"),u(r,"workOrderId"),s(r,"stageCode"),((Number)r.get("sequenceNo")).intValue(),s(r,"status"),n(r,"version"),i(r,"activatedAt"),i(r,"completedAt"));}
 private static String s(Map<String,Object> r,String k){Object v=r.get(k);return v==null?null:v.toString();} private static UUID u(Map<String,Object> r,String k){Object v=r.get(k);return v instanceof UUID x?x:UUID.fromString(v.toString());} private static long n(Map<String,Object> r,String k){return ((Number)r.get(k)).longValue();}
 private static Instant i(Map<String,Object> r,String k){Object v=r.get(k);if(v==null)return null;if(v instanceof Instant x)return x;if(v instanceof OffsetDateTime x)return x.toInstant();if(v instanceof java.sql.Timestamp x)return x.toInstant();return Instant.parse(v.toString());}
}
