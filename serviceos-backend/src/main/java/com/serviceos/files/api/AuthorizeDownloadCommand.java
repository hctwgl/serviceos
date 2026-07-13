package com.serviceos.files.api;

/** 下载目的会进入不可变审计记录，不允许使用含糊的空值。 */
public record AuthorizeDownloadCommand(String purpose) {
}
