package com.serviceos.identity.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 主体目录 MyBatis 查询边界，只允许 identity 模块查询适配器调用。 */
@Mapper
interface IdentityDirectoryQueryMapper {
    List<Map<String, Object>> findPage(
            @Param("tenantId") String tenantId,
            @Param("query") String query,
            @Param("status") String status,
            @Param("cursorName") String cursorName,
            @Param("cursorId") UUID cursorId,
            @Param("fetchSize") int fetchSize);
}
