package com.serviceos.bootstrap;

import org.jooq.ExecuteContext;
import org.jooq.ExecuteListenerProvider;
import org.jooq.impl.DefaultExecuteListener;
import org.jooq.impl.DefaultExecuteListenerProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.UncategorizedSQLException;

/**
 * jOOQ 异常转译兜底，恢复 JdbcClient 时代的统一契约：所有数据库错误都以
 * Spring {@link org.springframework.dao.DataAccessException} 上浮。
 *
 * <p>背景：Boot 4.1 的 ExceptionTranslatorExecuteListener 只在 SQLState 命中
 * {@code sql-error-codes.xml} 映射时替换为 Spring 具体异常（如 23505 →
 * DuplicateKeyException）；未映射状态（如 PL/pgSQL RAISE EXCEPTION 的 P0001）
 * 返回 null 且没有 Uncategorized 回退，jOOQ 原生异常会外泄到业务层与
 * {@link GlobalProblemHandler}。旧 JdbcTemplate 路径对未映射错误统一包装为
 * {@link UncategorizedSQLException}，本监听器恢复该语义。
 *
 * <p>排序在 Boot 转译器之后（LOWEST_PRECEDENCE）：已映射错误保持 Spring 具体
 * 异常类型不变，只兜底未被映射的 jOOQ 原生异常。
 */
@Configuration
class JooqExceptionTranslationConfiguration {

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    ExecuteListenerProvider uncategorizedJooqExceptionTranslator() {
        return new DefaultExecuteListenerProvider(new DefaultExecuteListener() {
            @Override
            public void exception(ExecuteContext ctx) {
                if (ctx.sqlException() != null
                        && ctx.exception() instanceof org.jooq.exception.DataAccessException) {
                    ctx.exception(new UncategorizedSQLException("jOOQ", ctx.sql(), ctx.sqlException()));
                }
            }
        });
    }
}
