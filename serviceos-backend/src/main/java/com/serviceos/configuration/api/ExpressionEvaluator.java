package com.serviceos.configuration.api;

import java.util.Map;

/** ADR-018 布尔条件层求值端口；无副作用、确定性。 */
public interface ExpressionEvaluator {
    ExpressionEvaluation evaluate(ExpressionDefinition expression, ExpressionContext context);

    /**
     * 发布期只校验语法、类型、白名单与复杂度，不读取任何业务事实。
     * 实现仍复用同一解析器，避免“发布校验器”和运行时解释器产生语义漂移。
     */
    default void validate(ExpressionDefinition expression) {
        throw new UnsupportedOperationException("表达式实现未提供发布期校验");
    }

    /**
     * 发布 Bundle 时按锁定 FORM 字段目录执行强类型校验。Map value 使用 FORM dataType 稳定代码；
     * 未声明字段与不支持比较的复合类型必须失败关闭。
     */
    default void validate(ExpressionDefinition expression, Map<String, String> formFieldTypes) {
        validate(expression);
    }
}
