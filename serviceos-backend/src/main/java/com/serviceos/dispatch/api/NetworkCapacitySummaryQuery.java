package com.serviceos.dispatch.api;

import java.util.List;

/**
 * 按网点读取 dsp_capacity_counter。调用方负责 Portal 上下文与 capability 鉴权。
 */
public interface NetworkCapacitySummaryQuery {
    List<NetworkCapacityCounterView> listForNetwork(String tenantId, String networkId);
}
