package com.serviceos.project.api;

/** 项目区域分工当前支持的固定岗位。 */
public enum ProjectPositionCode {
    CUSTOMER_SERVICE_MANAGER("客服经理"),
    PROJECT_MANAGER("项目经理"),
    PROJECT_ASSISTANT("项目助理");

    private final String label;

    ProjectPositionCode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
