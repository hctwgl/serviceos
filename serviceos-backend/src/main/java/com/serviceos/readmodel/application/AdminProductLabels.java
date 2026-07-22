package com.serviceos.readmodel.application;

import java.util.Map;

/**
 * Admin 黄金链路的受控产品语言映射。
 *
 * <p>未知技术码返回 {@code null}，由页面读模型明确标记数据不完整；绝不把英文枚举直接显示给用户。</p>
 */
final class AdminProductLabels {
    private static final Map<String, String> CLIENTS = Map.of(
            "BYD", "比亚迪",
            "REFERENCE_OEM", "合作车企"
    );
    private static final Map<String, String> SERVICES = Map.of(
            "HOME_CHARGING_SURVEY_INSTALL", "充电桩安装服务",
            "HOME_CHARGING_REPAIR", "充电桩维修服务",
            "ADMIN_PILOT_COMPLETION", "试点完工验证"
    );
    private static final Map<String, String> STATUSES = Map.of(
            "RECEIVED", "待受理",
            "ACTIVE", "履约中",
            "FULFILLED", "已完成",
            "CANCELLED", "已取消"
    );
    private static final Map<String, String> STAGES = Map.ofEntries(
            Map.entry("PILOT_INTAKE", "接单受理"),
            Map.entry("INTAKE", "接单受理"),
            Map.entry("PILOT_DISPATCH", "网点分配"),
            Map.entry("DISPATCH", "网点分配"),
            Map.entry("PILOT_APPOINTMENT", "预约确认"),
            Map.entry("APPOINTMENT", "预约确认"),
            Map.entry("PILOT_SURVEY", "上门勘测"),
            Map.entry("SURVEY", "上门勘测"),
            Map.entry("PILOT_INSTALLATION", "上门安装"),
            Map.entry("INSTALLATION", "上门安装"),
            Map.entry("FINAL_REVIEW", "资料审核"),
            Map.entry("CLIENT_CALLBACK", "车企回传"),
            Map.entry("PILOT_COMPLETION", "完工验证"),
            Map.entry("COMPLETED", "完成")
    );
    private static final Map<String, String> TASKS = Map.ofEntries(
            Map.entry("ASSIGN_COORDINATORS", "分配责任网点"),
            Map.entry("FIELD_SURVEY", "上门勘测"),
            Map.entry("FIELD_INSTALL", "上门安装"),
            Map.entry("SITE_SURVEY", "上门勘测"),
            Map.entry("INSTALLATION", "上门安装"),
            Map.entry("FINAL_REVIEW", "资料审核"),
            Map.entry("CLIENT_CALLBACK", "车企回传"),
            Map.entry("DISPATCH", "网点分配"),
            Map.entry("APPOINTMENT", "预约确认"),
            Map.entry("PILOT_SURVEY", "上门勘测"),
            Map.entry("PILOT_COMPLETION", "完工验证"),
            Map.entry("evidence.correction", "整改资料补充"),
            Map.entry("evidence.machine-validation", "资料自动校验"),
            Map.entry("evidence.review", "资料审核"),
            Map.entry("file.content-scan", "资料安全检查"),
            Map.entry("integration.byd.submit-review", "车企资料回传"),
            Map.entry("operations.resolve-exception", "运营异常处理")
    );

    private AdminProductLabels() {
    }

    static String client(String code) {
        return code == null ? null : CLIENTS.get(code);
    }

    static String service(String code) {
        return code == null ? null : SERVICES.get(code);
    }

    static String status(String code) {
        return code == null ? null : STATUSES.get(code);
    }

    static String stage(String code) {
        return code == null ? "尚未进入履约流程" : STAGES.get(code);
    }

    static String task(String code) {
        return code == null ? "暂无进行中的任务" : TASKS.get(code);
    }
}
