package com.serviceos.task.application;

import com.serviceos.authorization.api.*;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.Sha256;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
final class DefaultTaskDirectoryQueryService implements TaskDirectoryQueryService {
 private static final String READ="task.read";private static final Set<String> KINDS=Set.of("HUMAN","AUTOMATED");
 private static final Set<String> STATUSES=Set.of("READY","PENDING","CLAIMED","RUNNING","RETRY_WAIT","SUCCEEDED","COMPLETED","MANUAL_INTERVENTION","CANCELLED");
 private final TaskDirectoryQueryRepository queries;private final AuthorizationService authorization;private final ProjectScopeAuthorizationService scopes;private final Clock clock;
 DefaultTaskDirectoryQueryService(TaskDirectoryQueryRepository queries,AuthorizationService authorization,ProjectScopeAuthorizationService scopes,Clock clock){this.queries=queries;this.authorization=authorization;this.scopes=scopes;this.clock=clock;}
 @Override @Transactional(readOnly=true) public TaskDirectoryPage list(CurrentPrincipal principal,String correlationId,TaskDirectoryQuery q){
  Objects.requireNonNull(q,"query must not be null");if(q.limit()<1||q.limit()>100)throw new IllegalArgumentException("limit must be between 1 and 100");
  String kind=normalized(q.taskKind(),KINDS,"taskKind"),status=normalized(q.status(),STATUSES,"status"),assignee=assignee(q.assignee(),principal.principalId());
  AuthorizedProjectScope scope=scopes.require(principal,READ,"Task",correlationId);
  if(q.projectId()!=null&&!scope.tenantWide()&&!scope.projectIds().contains(q.projectId())){authorization.require(principal,AuthorizationRequest.projectCapability(READ,principal.tenantId(),"Task",q.projectId().toString(),q.projectId().toString()),correlationId);throw new IllegalStateException("任务项目范围拒绝未能失败关闭");}
  String filter=Sha256.digest("project="+nullable(q.projectId())+"|kind="+nullable(kind)+"|status="+nullable(status)+"|assignee="+nullable(q.assignee()));Cursor c=decode(q.cursor(),scope.scopeDigest(),filter);
  List<UUID> projects=scope.projectIds().stream().sorted(Comparator.comparing(UUID::toString)).toList();
  List<TaskDirectoryItem> fetched=queries.findPage(principal.tenantId(),scope.tenantWide(),projects,q.projectId(),kind,status,assignee,c==null?null:c.priority(),c==null?null:c.nextRunAt(),c==null?null:c.createdAt(),c==null?null:c.id(),q.limit()+1);
  boolean more=fetched.size()>q.limit();List<TaskDirectoryItem> selected=more?fetched.subList(0,q.limit()):fetched;TaskDirectoryItem last=more?selected.getLast():null;
  return new TaskDirectoryPage(selected,last==null?null:encode(scope.scopeDigest(),filter,last),clock.instant());
 }
 @Override @Transactional(readOnly=true) public TaskDetail get(CurrentPrincipal principal,String correlationId,UUID taskId){
  TaskDetail detail=queries.findDetail(principal.tenantId(),taskId,clock.instant()).orElseThrow(()->new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND,"任务不存在"));
  AuthorizationRequest request=detail.task().projectId()==null?AuthorizationRequest.tenantCapability(READ,principal.tenantId(),"Task",taskId.toString()):AuthorizationRequest.projectCapability(READ,principal.tenantId(),"Task",taskId.toString(),detail.task().projectId().toString());authorization.require(principal,request,correlationId);return detail;
 }
 private static String normalized(String v,Set<String> allowed,String name){if(v==null)return null;if(v.isBlank()||!v.equals(v.trim())||!allowed.contains(v))throw new IllegalArgumentException(name+" is invalid");return v;}
 private static String assignee(String v,String principal){if(v==null)return null;if(!"me".equals(v))throw new IllegalArgumentException("assignee must be me");return principal;}
 private static String encode(String scope,String filter,TaskDirectoryItem x){return Base64.getUrlEncoder().withoutPadding().encodeToString((scope+"|"+filter+"|"+x.priority()+"|"+x.nextRunAt()+"|"+x.createdAt()+"|"+x.id()).getBytes(StandardCharsets.UTF_8));}
 private static Cursor decode(String v,String scope,String filter){if(v==null)return null;try{String[] p=new String(Base64.getUrlDecoder().decode(v),StandardCharsets.UTF_8).split("\\|",-1);if(p.length!=6||!scope.equals(p[0])||!filter.equals(p[1]))throw new IllegalArgumentException();return new Cursor(Integer.parseInt(p[2]),Instant.parse(p[3]),Instant.parse(p[4]),UUID.fromString(p[5]));}catch(RuntimeException e){throw new IllegalArgumentException("cursor is invalid for the requested task scope",e);}}
 private static String nullable(Object v){return v==null?"-":v.toString();}private record Cursor(int priority,Instant nextRunAt,Instant createdAt,UUID id){}
}
