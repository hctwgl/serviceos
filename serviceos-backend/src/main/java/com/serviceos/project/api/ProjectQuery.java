package com.serviceos.project.api;

import java.time.LocalDate;

/** 项目目录查询；筛选值和 cursor 均由服务端校验，不能在非法时退化为无筛选。 */
public record ProjectQuery(
        String clientId,
        String status,
        LocalDate activeOn,
        String cursor,
        int limit
) {
}
