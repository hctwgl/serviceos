package com.serviceos.network.api;

import java.util.Collection;
import java.util.List;

/**
 * ACTIVE ServiceNetwork 目录。
 *
 * <p>入参为项目目录中的 network_id 字符串；仅保留能对应到 ACTIVE {@code net_service_network}
 * 的标识（UUID 文本形式）。</p>
 */
public interface ServiceNetworkDirectoryQuery {
    List<String> listActiveNetworkIds(String tenantId, Collection<String> networkIds);
}
