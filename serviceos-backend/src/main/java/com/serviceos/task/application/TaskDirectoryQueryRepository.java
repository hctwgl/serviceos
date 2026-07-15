package com.serviceos.task.application;

import com.serviceos.task.api.*;
import java.time.Instant;
import java.util.*;

public interface TaskDirectoryQueryRepository {
 List<TaskDirectoryItem> findPage(String tenantId,boolean tenantWide,List<UUID> projectIds,UUID projectId,
  String taskKind,String status,String assigneeId,Integer cursorPriority,Instant cursorNextRunAt,
  Instant cursorCreatedAt,UUID cursorId,int fetchSize);
 Optional<TaskDetail> findDetail(String tenantId,UUID taskId,Instant asOf);
}
