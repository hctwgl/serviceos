package com.serviceos.readmodel.api;

import java.util.List;

/** 搜索元数据：用 qDigest 代替原文，避免敏感 query 回显。 */
public record ControlledSearchMeta(
        String qDigest,
        List<ControlledSearchType> requestedTypes,
        List<ControlledSearchType> searchedTypes,
        List<ControlledSearchType> omittedTypes
) {}
