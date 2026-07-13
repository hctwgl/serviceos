package com.serviceos.files.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.util.UUID;

/**
 * 文件生命周期写接口。业务模块只能保存返回的 fileId，不能自行拼接对象 key 或永久 URL。
 */
public interface FileCommandService {
    UploadSessionView beginUpload(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            BeginUploadCommand command
    );

    StoredFileView finalizeUpload(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            UUID uploadSessionId,
            FinalizeUploadCommand command
    );

    DownloadAuthorizationView authorizeDownload(
            CurrentPrincipal principal,
            String correlationId,
            UUID fileId,
            AuthorizeDownloadCommand command
    );
}
