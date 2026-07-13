package com.serviceos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ServiceOS 模块化单体唯一启动入口。
 *
 * <p>业务模块位于 {@code com.serviceos} 根包下，由 Spring Modulith 自动发现并验证边界。
 * API 与后台 worker 首期共享同一版本产物，后续通过 profile 控制实例职责，而不是提前拆成微服务。</p>
 */
@SpringBootApplication
public class ServiceOsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceOsApplication.class, args);
    }
}
