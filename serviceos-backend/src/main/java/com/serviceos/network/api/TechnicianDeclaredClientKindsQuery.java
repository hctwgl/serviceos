package com.serviceos.network.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 师傅履约端声明只读查询（ADR-088 A2-R）。
 *
 * <p>供派单模块在不经目录能力门禁的情况下读取权威声明；调用方负责租户与用例鉴权。</p>
 */
public interface TechnicianDeclaredClientKindsQuery {
    /**
     * @return empty=档案不存在；present 时 {@code kinds()==null} 表示未声明
     */
    Optional<DeclaredKinds> findDeclaredSupportedClientKinds(String tenantId, UUID technicianProfileId);

    /** {@code kinds} 为 null 表示未声明（与资产定向 null 语义对齐）。 */
    record DeclaredKinds(List<String> kinds) {
        public DeclaredKinds {
            kinds = kinds == null ? null : List.copyOf(kinds);
        }
    }
}
