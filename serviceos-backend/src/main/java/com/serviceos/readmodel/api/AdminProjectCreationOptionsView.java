package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Admin 创建项目页面级读模型。
 *
 * <p>车企、行政区和服务网点均由服务端使用权威目录一次组合，前端不得再调用多个领域目录
 * 自行拼接可选项。allowedActions 只表达当前页面动作，不替代创建命令的实时授权。</p>
 */
public record AdminProjectCreationOptionsView(
        List<ClientOption> clients,
        List<RegionOption> regions,
        List<NetworkOption> networks,
        List<String> allowedActions,
        Instant asOf
) {
    public AdminProjectCreationOptionsView {
        clients = clients == null ? List.of() : List.copyOf(clients);
        regions = regions == null ? List.of() : List.copyOf(regions);
        networks = networks == null ? List.of() : List.copyOf(networks);
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
    }

    public record ClientOption(String code, String name) {}

    public record RegionOption(String code, String name, String level, String parentCode) {}

    public record NetworkOption(UUID id, String code, String name, String status) {}
}
