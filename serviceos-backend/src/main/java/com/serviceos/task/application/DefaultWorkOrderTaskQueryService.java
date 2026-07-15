package com.serviceos.task.application;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.task.api.*;
import com.serviceos.workorder.api.WorkOrderQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
final class DefaultWorkOrderTaskQueryService implements WorkOrderTaskQueryService {
 private final WorkOrderQueryService workOrders;private final WorkOrderTaskQueryRepository queries;private final Clock clock;
 DefaultWorkOrderTaskQueryService(WorkOrderQueryService workOrders,WorkOrderTaskQueryRepository queries,Clock clock){this.workOrders=workOrders;this.queries=queries;this.clock=clock;}
 @Override @Transactional(readOnly=true) public WorkOrderTaskPage list(CurrentPrincipal principal,String correlationId,UUID workOrderId,String cursorValue,int limit){
  if(limit<1||limit>100)throw new IllegalArgumentException("limit must be between 1 and 100");
  workOrders.get(principal,correlationId,workOrderId);Cursor cursor=decode(cursorValue,workOrderId);
  List<WorkOrderTaskSummary> fetched=queries.findPage(principal.tenantId(),workOrderId,cursor==null?null:cursor.createdAt(),cursor==null?null:cursor.id(),limit+1);
  boolean more=fetched.size()>limit;List<WorkOrderTaskSummary> selected=more?fetched.subList(0,limit):fetched;WorkOrderTaskSummary last=more?selected.getLast():null;
  return new WorkOrderTaskPage(selected,last==null?null:encode(workOrderId,last.createdAt(),last.id()),clock.instant());
 }
 private static String encode(UUID workOrderId,Instant createdAt,UUID id){return Base64.getUrlEncoder().withoutPadding().encodeToString((workOrderId+"|"+createdAt+"|"+id).getBytes(StandardCharsets.UTF_8));}
 private static Cursor decode(String value,UUID workOrderId){if(value==null)return null;try{String[] p=new String(Base64.getUrlDecoder().decode(value),StandardCharsets.UTF_8).split("\\|",-1);if(p.length!=3||!workOrderId.toString().equals(p[0]))throw new IllegalArgumentException();return new Cursor(Instant.parse(p[1]),UUID.fromString(p[2]));}catch(RuntimeException e){throw new IllegalArgumentException("cursor is invalid for the requested work order tasks",e);}}
 private record Cursor(Instant createdAt,UUID id){}
}
