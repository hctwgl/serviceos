package com.serviceos.project.api;

import java.time.LocalDate;
import java.util.List;

/**
 * 创建运营项目命令。项目不是车企枚举，而是有生效区间的运营边界。
 */
public record CreateProjectCommand(
        String code,
        String clientId,
        String name,
        LocalDate startsOn,
        LocalDate endsOn,
        List<String> regionCodes
) {
}
