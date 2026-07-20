package com.serviceos.configuration.api;

/**
 * 正式建单履约配置解析器。无匹配时失败关闭，禁止回退草稿或默认流程。
 */
public interface ProjectFulfillmentResolver {
    ProjectFulfillmentResolveResult resolve(ProjectFulfillmentResolveQuery query);
}
