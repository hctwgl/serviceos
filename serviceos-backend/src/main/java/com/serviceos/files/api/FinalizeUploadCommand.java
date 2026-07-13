package com.serviceos.files.api;

/**
 * 完成上传。finalizeCommandId 用于设备离线重放；同一会话只能生成一个文件对象。
 */
public record FinalizeUploadCommand(String actualSha256, String finalizeCommandId) {
}
