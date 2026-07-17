package com.serviceos.readmodel.api;

/** 单条受控筛选子句。M189 仅接受 EQ，对应页面 OpenAPI query 等值参数。 */
public record SavedViewFilterClause(String field, String operator, String value) {
}
