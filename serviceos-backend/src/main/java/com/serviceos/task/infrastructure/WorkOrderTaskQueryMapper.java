package com.serviceos.task.infrastructure;

import org.apache.ibatis.annotations.*;
import java.time.Instant;
import java.util.*;

@Mapper
interface WorkOrderTaskQueryMapper {
 List<Map<String,Object>> findPage(@Param("tenantId") String tenantId,@Param("workOrderId") UUID workOrderId,@Param("cursorCreatedAt") Instant cursorCreatedAt,@Param("cursorId") UUID cursorId,@Param("fetchSize") int fetchSize);
}
