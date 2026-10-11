package cn.iocoder.yudao.module.infra.controller.admin.file;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.infra.service.file.PrivateFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** Private attachments never enter the public /infra/file/{configId}/get/** namespace. */
@Tag(name = "管理后台 - 私有附件（需接入对象授权）")
@RestController
@RequestMapping("/infra/private-file")
@PreAuthorize("isAuthenticated()")
public class PrivateFileController {

    private final PrivateFileService fileService;

    public PrivateFileController(PrivateFileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传私有附件", description = "无对象授权适配器时默认拒绝；不返回公开 URL")
    public CommonResult<String> upload(@RequestParam("objectType") String objectType,
                                       @RequestParam("objectId") String objectId,
                                       @RequestParam("purpose") String purpose,
                                       @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > PrivateFileService.MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Private attachments must be between 1 byte and 10 MiB");
        }
        try (InputStream input = file.getInputStream()) {
            byte[] content = input.readNBytes(PrivateFileService.MAX_FILE_SIZE + 1);
            return success(fileService.upload(objectType, objectId, purpose, file.getOriginalFilename(), content));
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "下载私有附件", description = "每次请求重新校验当前技术主体、租户与业务对象权限")
    public void download(@PathVariable("id") String id, HttpServletResponse response) throws IOException {
        // Avoid caching even a refusal response at a proxy/shared browser.
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, private");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setHeader("X-Content-Type-Options", "nosniff");
        PrivateFileService.Download file = fileService.download(id);
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(file.name(), StandardCharsets.UTF_8).build().toString());
        response.setContentLength(file.content().length);
        response.getOutputStream().write(file.content());
    }

}
