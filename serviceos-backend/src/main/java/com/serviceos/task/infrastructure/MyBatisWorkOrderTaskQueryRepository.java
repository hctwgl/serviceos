package com.serviceos.task.infrastructure;

import com.serviceos.task.api.WorkOrderTaskSummary;
import com.serviceos.task.application.WorkOrderTaskQueryRepository;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;

@Repository
final class MyBatisWorkOrderTaskQueryRepository implements WorkOrderTaskQueryRepository {
 private final WorkOrderTaskQueryMapper mapper;MyBatisWorkOrderTaskQueryRepository(WorkOrderTaskQueryMapper mapper){this.mapper=mapper;}
 public List<WorkOrderTaskSummary> findPage(String tenant,UUID workOrder,Instant at,UUID id,int size){return mapper.findPage(tenant,workOrder,at,id,size).stream().map(MyBatisWorkOrderTaskQueryRepository::view).toList();}
 private static WorkOrderTaskSummary view(Map<String,Object> r){return new WorkOrderTaskSummary(u(r,"id"),u(r,"projectId"),u(r,"workOrderId"),u(r,"workflowInstanceId"),u(r,"stageInstanceId"),u(r,"workflowNodeInstanceId"),s(r,"workflowNodeId"),s(r,"stageCode"),s(r,"taskType"),s(r,"taskKind"),((Number)r.get("priority")).intValue(),s(r,"status"),i(r,"nextRunAt"),s(r,"claimedBy"),i(r,"claimedAt"),i(r,"startedAt"),i(r,"completedAt"),((Number)r.get("version")).longValue(),i(r,"createdAt"),i(r,"updatedAt"));}
 private static String s(Map<String,Object> r,String k){Object v=r.get(k);return v==null?null:v.toString();}private static UUID u(Map<String,Object> r,String k){Object v=r.get(k);return v==null?null:v instanceof UUID x?x:UUID.fromString(v.toString());}private static Instant i(Map<String,Object> r,String k){Object v=r.get(k);if(v==null)return null;if(v instanceof Instant x)return x;if(v instanceof OffsetDateTime x)return x.toInstant();if(v instanceof java.sql.Timestamp x)return x.toInstant();return Instant.parse(v.toString());}
}
