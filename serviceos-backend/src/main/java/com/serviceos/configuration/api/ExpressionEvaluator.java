package com.serviceos.configuration.api;

/** ADR-018 布尔条件层求值端口；无副作用、确定性。 */
public interface ExpressionEvaluator {
    ExpressionEvaluation evaluate(ExpressionDefinition expression, ExpressionContext context);

    /**
     * 发布期只校验语法、类型、白名单与复杂度，不读取任何业务事实。
     * 实现仍复用同一解析器，避免“发布校验器”和运行时解释器产生语义漂移。
     */
    default void validate(ExpressionDefinition expression) {
        String sentinel = "__STATIC_VALIDATION__";
        evaluate(expression, new ExpressionContext(
                new ExpressionContext.WorkOrderContext(sentinel, sentinel, sentinel),
                new ExpressionContext.RegionContext(sentinel, sentinel, sentinel),
                new ExpressionContext.TaskContext(sentinel, sentinel)));
    }
}
