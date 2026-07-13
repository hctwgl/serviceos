package com.serviceos.shared;

/**
 * 可安全映射到 Problem Details 的业务异常；不得在 message 中包含敏感字段或内部 SQL。
 */
public final class BusinessProblem extends RuntimeException {
    private final ProblemCode code;

    public BusinessProblem(ProblemCode code, String message) {
        super(message);
        this.code = code;
    }

    public ProblemCode code() {
        return code;
    }
}
