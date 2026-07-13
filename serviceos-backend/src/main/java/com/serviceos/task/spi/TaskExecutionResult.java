package com.serviceos.task.spi;

/**
 * 已确认成功的执行结果。外部结果未知不得伪装为成功，应抛出 UNKNOWN 类型异常。
 */
public record TaskExecutionResult(String resultRef) {
    public static TaskExecutionResult succeeded(String resultRef) {
        return new TaskExecutionResult(resultRef);
    }
}
