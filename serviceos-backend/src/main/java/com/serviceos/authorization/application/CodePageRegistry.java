package com.serviceos.authorization.application;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * M188 代码注册 Page Registry。CONSUMER Persona 不注册任何页面入口。
 * navigationCatalogVersion 随注册表语义变更递增。
 */
@Component
final class CodePageRegistry {
    static final String CATALOG_VERSION = "page-registry-v1";

    private final List<RegisteredPage> pages = List.of(
            // ADMIN
            page("ADMIN.WORKBENCH", "ADMIN", "workbench", "工作台", 10, "核心",
                    List.of("workOrder.read"), null),
            page("ADMIN.WORK_ORDER.LIST", "ADMIN", "work-orders", "工单目录", 20, "工单履约",
                    List.of("workOrder.read"), null),
            page("ADMIN.TASK.DIRECTORY", "ADMIN", "tasks", "任务目录", 30, "工单履约",
                    List.of("task.read"), null),
            page("ADMIN.REVIEW.QUEUE", "ADMIN", "reviews", "审核队列", 40, "资料审核",
                    List.of("evidence.review"), null),
            page("ADMIN.CORRECTION.QUEUE", "ADMIN", "corrections", "整改跟踪", 50, "资料审核",
                    List.of("evidence.review"), null),
            page("ADMIN.EXCEPTION.QUEUE", "ADMIN", "exceptions", "运营异常", 60, "异常与集成",
                    List.of("operations.exception.read"), null),
            page("ADMIN.INTEGRATION.INBOUND", "ADMIN", "integration/inbound", "入站队列", 70, "异常与集成",
                    List.of("integration.readInbound"), null),
            page("ADMIN.INTEGRATION.OUTBOUND", "ADMIN", "integration/outbound", "外发交付", 80, "异常与集成",
                    List.of("integration.readOutbound"), null),
            page("ADMIN.SLA.WORKBENCH", "ADMIN", "sla", "SLA 工作台", 90, "核心",
                    List.of("sla.read"), null),
            page("ADMIN.PROJECT.DIRECTORY", "ADMIN", "projects", "项目目录", 100, "项目与配置",
                    List.of("project.read"), null),
            page("ADMIN.USER.DIRECTORY", "ADMIN", "users", "用户目录", 200, "平台治理",
                    List.of("identity.read"), null),
            page("ADMIN.ORGANIZATION.DIRECTORY", "ADMIN", "organizations", "企业组织", 210, "平台治理",
                    List.of("organization.read"), null),
            page("ADMIN.NETWORK.DIRECTORY", "ADMIN", "networks", "合作组织与网点", 220, "平台治理",
                    List.of("network.read"), null),
            page("ADMIN.TECHNICIAN.DIRECTORY", "ADMIN", "technicians", "师傅档案", 230, "平台治理",
                    List.of("network.read"), null),
            page("ADMIN.ROLE.DIRECTORY", "ADMIN", "roles", "角色与 Capability", 240, "平台治理",
                    List.of("authorization.read"), null),
            page("ADMIN.GRANT.DIRECTORY", "ADMIN", "grants", "授权与委托", 250, "平台治理",
                    List.of("authorization.read"), null),

            // NETWORK（独立 Portal 可消费；本仓库以 API 与最小 stub 接入）
            page("NETWORK.WORKBENCH", "NETWORK", "workbench", "本网点工作台", 10, "核心",
                    List.of("networkTask.read"), null),
            page("NETWORK.TASK.QUEUE", "NETWORK", "tasks", "工单任务", 20, "工单任务",
                    List.of("networkTask.read"), null),
            page("NETWORK.TECHNICIAN.LIST", "NETWORK", "technicians", "本网点师傅", 30, "人员与能力",
                    List.of("technician.readOwnNetwork"), null),

            // TECHNICIAN
            page("TECHNICIAN.TASK.LIST", "TECHNICIAN", "tasks", "任务", 10, "底部导航",
                    List.of("task.readAssigned"), null),
            page("TECHNICIAN.SCHEDULE", "TECHNICIAN", "schedule", "日程", 20, "底部导航",
                    List.of("appointment.propose"), null),
            page("TECHNICIAN.ME", "TECHNICIAN", "me", "我的", 40, "底部导航",
                    List.of("task.readAssigned"), null)
    );

    List<RegisteredPage> all() {
        return pages;
    }

    List<RegisteredPage> forPortal(String portal) {
        return pages.stream().filter(page -> page.portal().equals(portal)).toList();
    }

    private static RegisteredPage page(
            String pageId, String portal, String routeKey, String title, int order, String section,
            List<String> capabilities, String featureGate
    ) {
        return new RegisteredPage(pageId, portal, routeKey, title, order, section, capabilities, featureGate);
    }
}
