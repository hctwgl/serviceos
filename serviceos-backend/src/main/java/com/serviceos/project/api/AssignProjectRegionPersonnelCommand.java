package com.serviceos.project.api;

import java.util.UUID;

/** 明确新增或替换一个项目区域岗位负责人。 */
public record AssignProjectRegionPersonnelCommand(
        UUID projectId,
        String regionCode,
        ProjectPositionCode position,
        UUID principalId,
        UUID expectedCurrentAssignmentId,
        boolean allowInheritance,
        String reason
) {
    public AssignProjectRegionPersonnelCommand {
        if (projectId == null || principalId == null || position == null) {
            throw new IllegalArgumentException("项目、岗位和人员不能为空");
        }
        if (regionCode == null || regionCode.isBlank() || !regionCode.equals(regionCode.trim())
                || regionCode.length() > 32) {
            throw new IllegalArgumentException("行政区编码无效");
        }
        if (reason == null || reason.isBlank() || !reason.equals(reason.trim()) || reason.length() > 500) {
            throw new IllegalArgumentException("请填写 1～500 字的分工变更原因");
        }
    }
}
