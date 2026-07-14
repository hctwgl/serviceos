package com.serviceos.forms.api;

/** 服务端字段校验结果；code 是客户端可依赖的稳定语义，message 仅用于展示。 */
public record FormValidationIssue(String fieldKey, String code, String message) {
}
