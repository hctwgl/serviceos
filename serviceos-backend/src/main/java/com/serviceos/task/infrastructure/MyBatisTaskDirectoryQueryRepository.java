package com.serviceos.task.infrastructure;

import com.serviceos.task.api.*;
import com.serviceos.task.application.TaskDirectoryQueryRepository;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;

@Repository
final class MyBatisTaskDirectoryQueryRepository implements TaskDirectoryQueryRepository {
 private static final JsonMapper JSON=JsonMapper.builder().build();private final TaskDirectoryQueryMapper mapper;
 MyBatisTaskDirectoryQueryRepository(TaskDirectoryQueryMapper mapper){this.mapper=mapper;}
 public List<TaskDirectoryItem> findPage(String tenant,boolean wide,List<UUID> projects,UUID project,String kind,String status,String assignee,Integer priority,Instant next,Instant created,UUID id,int size){return mapper.findPage(tenant,wide,projects.stream().map(UUID::toString).toList(),project,kind,status,assignee,priority,next,created,id,size).stream().map(MyBatisTaskDirectoryQueryRepository::item).toList();}
 public Optional<TaskDetail> findDetail(String tenant,UUID id,Instant asOf){return Optional.ofNullable(mapper.findDetail(tenant,id)).map(r->detail(r,asOf));}
 private static TaskDirectoryItem item(Map<String,Object> r){return new TaskDirectoryItem(u(r,"id"),u(r,"projectId"),u(r,"workOrderId"),s(r,"taskType"),s(r,"taskKind"),s(r,"stageCode"),num(r,"priority").intValue(),s(r,"status"),i(r,"nextRunAt"),s(r,"claimedBy"),num(r,"attemptCount").intValue(),num(r,"maxAttempts").intValue(),num(r,"version").longValue(),i(r,"createdAt"),i(r,"updatedAt"));}
 private static TaskDetail detail(Map<String,Object> r,Instant asOf){return new TaskDetail(item(r),u(r,"workflowInstanceId"),u(r,"stageInstanceId"),u(r,"workflowNodeInstanceId"),s(r,"workflowNodeId"),u(r,"workflowDefinitionVersionId"),s(r,"workflowDefinitionDigest"),u(r,"configurationBundleId"),s(r,"configurationBundleDigest"),s(r,"formRef"),s(r,"responsibleUserId"),strings(r,"candidateUserIds"),i(r,"claimedAt"),i(r,"startedAt"),i(r,"completedAt"),s(r,"resultRef"),s(r,"resultDigest"),refs(r,"inputVersionRefs"),asOf);}
 private static List<String> strings(Map<String,Object> r,String k){try{return List.of(JSON.readValue(s(r,k),String[].class));}catch(JacksonException e){throw new IllegalStateException("任务详情包含非法候选人 JSON",e);}}
 private static List<InputVersionRef> refs(Map<String,Object> r,String k){try{return List.of(JSON.readValue(s(r,k),InputVersionRef[].class));}catch(JacksonException e){throw new IllegalStateException("任务详情包含非法输入版本 JSON",e);}}
 private static String s(Map<String,Object> r,String k){Object v=r.get(k);return v==null?null:v.toString();}private static UUID u(Map<String,Object> r,String k){Object v=r.get(k);return v==null?null:v instanceof UUID x?x:UUID.fromString(v.toString());}private static Number num(Map<String,Object> r,String k){return (Number)r.get(k);}private static Instant i(Map<String,Object> r,String k){Object v=r.get(k);if(v==null)return null;if(v instanceof Instant x)return x;if(v instanceof OffsetDateTime x)return x.toInstant();if(v instanceof java.sql.Timestamp x)return x.toInstant();return Instant.parse(v.toString());}
}
