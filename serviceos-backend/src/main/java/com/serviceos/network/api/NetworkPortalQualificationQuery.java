package com.serviceos.network.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 列出网点 ACTIVE 师傅的资质。无目录 network.read 门禁；调用方负责 Portal 鉴权。
 *
 * <p>与 {@link NetworkPortalTechnicianQuery} 同约定：本端口只做目录 fan-in，
 * 不校验 Portal membership / capability。</p>
 */
public interface NetworkPortalQualificationQuery {
    /**
     * 返回本网点当前有效 ACTIVE NetworkTechnicianMembership 师傅集合上的全部资质。
     */
    List<TechnicianQualificationView> listForActiveTechnicians(String tenantId, UUID networkId);

    /**
     * 按资质 ID 读取。不校验师傅是否属于某网点；Portal 编排层须再核 ACTIVE 成员资格。
     */
    Optional<TechnicianQualificationView> findById(String tenantId, UUID qualificationId);
}
