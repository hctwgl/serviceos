package com.serviceos.task.api;

import java.util.UUID;

/** task.read 队列筛选；assignee 仅接受 me。 */
public record TaskDirectoryQuery(UUID projectId,String taskKind,String status,String assignee,String cursor,int limit) {}
