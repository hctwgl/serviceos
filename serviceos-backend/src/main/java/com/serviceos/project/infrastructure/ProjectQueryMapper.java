package com.serviceos.project.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** project 查询 SQL 边界，只允许同模块查询 Repository 适配器调用。 */
@Mapper
interface ProjectQueryMapper {
    List<Map<String, Object>> findPage(
            @Param("tenantId") String tenantId,
            @Param("tenantWide") boolean tenantWide,
            @Param("projectIds") List<String> projectIds,
            @Param("clientId") String clientId,
            @Param("status") String status,
            @Param("activeOn") LocalDate activeOn,
            @Param("cursorCode") String cursorCode,
            @Param("cursorId") UUID cursorId,
            @Param("fetchSize") int fetchSize);

    List<Map<String, Object>> findScopeRevisionPage(
            @Param("tenantId") String tenantId,
            @Param("projectId") UUID projectId,
            @Param("cursorVersion") Long cursorVersion,
            @Param("fetchSize") int fetchSize);
}
