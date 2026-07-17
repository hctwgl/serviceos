package com.serviceos.readmodel.api;

import java.util.List;

/** 受控筛选 AST：字段与操作符必须来自页面已 Accepted 的 OpenAPI 查询目录。 */
public record SavedViewFilterAst(List<SavedViewFilterClause> clauses) {
    public SavedViewFilterAst {
        clauses = clauses == null ? List.of() : List.copyOf(clauses);
    }
}
