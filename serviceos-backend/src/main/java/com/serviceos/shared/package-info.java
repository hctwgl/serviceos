/**
 * 稳定共享内核：只允许放置跨模块通用且不含业务策略的值对象。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Shared Kernel",
        type = org.springframework.modulith.ApplicationModule.Type.OPEN
)
package com.serviceos.shared;
