package com.serviceos.authorization.application;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 代码注册 Page Registry。CONSUMER Persona 不注册任何页面入口。
 * navigationCatalogVersion 随注册表语义变更递增。
 *
 * <p>Admin section 对齐产品 IA 九个一级菜单；前端必须原样消费 section，不得正则重分组。</p>
 */
@Component
final class CodePageRegistry {
    static final String CATALOG_VERSION = "page-registry-v18";

    private final List<RegisteredPage> pages = List.of(
            // ADMIN — section = 产品一级菜单
            page("ADMIN.WORKBENCH", "ADMIN", "workbench", "工作台", 10, "工作台",
                    List.of("workOrder.read"), null),
            page("ADMIN.SEARCH", "ADMIN", "search", "全局搜索", 15, "工作台",
                    List.of("search.read"), null),
            page("ADMIN.SLA.WORKBENCH", "ADMIN", "sla", "SLA 风险", 18, "工作台",
                    List.of("sla.read"), null),
            page("ADMIN.EXCEPTION.QUEUE", "ADMIN", "exceptions", "运营异常", 19, "工作台",
                    List.of("operations.exception.read"), null),
            page("ADMIN.WORK_ORDER.LIST", "ADMIN", "work-orders", "工单中心", 20, "工单运营",
                    List.of("workOrder.read"), null),
            page("ADMIN.TASK.DIRECTORY", "ADMIN", "tasks", "服务任务", 30, "服务履约",
                    List.of("task.read"), null),
            page("ADMIN.REVIEW.QUEUE", "ADMIN", "reviews", "审核队列", 40, "审核与整改",
                    List.of("evidence.review"), null),
            page("ADMIN.CORRECTION.QUEUE", "ADMIN", "corrections", "整改跟踪", 50, "审核与整改",
                    List.of("evidence.review"), null),
            page("ADMIN.PROJECT.DIRECTORY", "ADMIN", "projects", "项目管理", 100, "客户与项目",
                    List.of("project.read"), null),
            page("ADMIN.NETWORK.DIRECTORY", "ADMIN", "networks", "合作组织与网点", 220, "组织与资源",
                    List.of("network.read"), null),
            page("ADMIN.TECHNICIAN.DIRECTORY", "ADMIN", "technicians", "师傅档案", 230, "组织与资源",
                    List.of("network.read"), null),
            page("ADMIN.CONFIGURATION.DESIGNER", "ADMIN", "configuration/designer", "配置设计器", 110, "配置中心",
                    List.of("configuration.draft.write"), null),
            page("ADMIN.USER.DIRECTORY", "ADMIN", "users", "用户管理", 200, "系统管理",
                    List.of("identity.read"), null),
            page("ADMIN.ORGANIZATION.DIRECTORY", "ADMIN", "organizations", "组织管理", 210, "系统管理",
                    List.of("organization.read"), null),
            page("ADMIN.ROLE.DIRECTORY", "ADMIN", "roles", "角色管理", 240, "系统管理",
                    List.of("authorization.read"), null),
            page("ADMIN.GRANT.DIRECTORY", "ADMIN", "grants", "授权与委托", 250, "系统管理",
                    List.of("authorization.read"), null),
            page("ADMIN.INTEGRATION.INBOUND", "ADMIN", "integration/inbound", "入站记录", 70, "审计与监控",
                    List.of("integration.readInbound"), null),
            page("ADMIN.INTEGRATION.OUTBOUND", "ADMIN", "integration/outbound", "外发记录", 80, "审计与监控",
                    List.of("integration.readOutbound"), null),

            // NETWORK（独立 Portal；导航仍非授权真相）
            page("NETWORK.WORKBENCH", "NETWORK", "workbench", "本网点工作台", 10, "核心",
                    List.of("networkTask.read"), null),
            page("NETWORK.WORKORDER.LIST", "NETWORK", "work-orders", "本网点工单", 15, "工单任务",
                    List.of("networkTask.read"), null),
            page("NETWORK.WORKORDER.WORKSPACE", "NETWORK", "work-order-workspace", "工单工作区", 16, "工单任务",
                    List.of("networkTask.read"), null),
            page("NETWORK.TASK.QUEUE", "NETWORK", "tasks", "工单任务", 20, "工单任务",
                    List.of("networkTask.read"), null),
            page("NETWORK.TECHNICIAN.LIST", "NETWORK", "technicians", "本网点师傅", 30, "人员与能力",
                    List.of("technician.readOwnNetwork", "networkPortal.manageTechnician"), null),
            page("NETWORK.QUALIFICATION", "NETWORK", "technicians/qualifications", "资质与到期", 32, "人员与能力",
                    List.of("networkPortal.manageTechnician", "technician.readOwnNetwork"), null),
            page("NETWORK.TECHNICIAN.ASSIGN", "NETWORK", "tasks/assign-technician", "分配师傅", 25, "工单任务",
                    List.of("networkPortal.assignTechnician", "networkPortal.reassignTechnician"), null),
            page("NETWORK.APPOINTMENT", "NETWORK", "tasks/appointments", "本网点预约", 28, "工单任务",
                    List.of("networkPortal.manageAppointment"), null),
            page("NETWORK.EVIDENCE.SUPPLEMENT", "NETWORK", "tasks/evidence-supplement", "资料代补", 29, "工单任务",
                    List.of("evidence.submitOnBehalf"), null),
            page("NETWORK.CORRECTION.QUEUE", "NETWORK", "corrections", "本网点整改", 27, "工单任务",
                    List.of("evidence.read"), null),
            page("NETWORK.EXCEPTION.QUEUE", "NETWORK", "exceptions", "本网点异常", 26, "工单任务",
                    List.of("operations.exception.read"), null),
            page("NETWORK.CAPACITY", "NETWORK", "capacity", "本网点产能", 35, "人员与能力",
                    List.of("networkTask.read"), null),

            // TECHNICIAN
            page("TECHNICIAN.TASK.LIST", "TECHNICIAN", "task-feed", "任务 Feed", 10, "底部导航",
                    List.of("task.readAssigned"), null),
            page("TECHNICIAN.SCHEDULE", "TECHNICIAN", "schedule", "日程", 20, "底部导航",
                    List.of("task.readAssigned"), null),
            page("TECHNICIAN.SYNC.SUMMARY", "TECHNICIAN", "sync-summary", "同步摘要", 30, "底部导航",
                    List.of("task.readAssigned"), null),
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
