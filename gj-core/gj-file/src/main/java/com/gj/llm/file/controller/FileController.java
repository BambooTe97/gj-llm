package com.gj.llm.file.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gj.llm.common.web.ApiResponse;
import com.gj.llm.file.model.FileInfo;
import com.gj.llm.file.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件管理控制器 —— 提供文件上传、分页列表、下载、删除接口。
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>POST   /api/files/upload     — 上传文件</li>
 *   <li>GET    /api/files             — 分页文件列表</li>
 *   <li>GET    /api/files/{id}        — 下载/预览文件</li>
 *   <li>DELETE /api/files/{id}        — 删除文件</li>
 * </ul>
 *
 * @author gj-llm
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    /**
     * 上传单个文件。
     */
    @PostMapping("/upload")
    public ApiResponse<FileInfo> upload(@RequestParam("file") MultipartFile file) {
        try {
            FileInfo fileInfo = fileStorageService.upload(file);
            return ApiResponse.ok(fileInfo, "上传成功");
        } catch (RuntimeException e) {
            log.warn("文件上传失败: {}", e.getMessage());
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    /**
     * 分页查询文件列表。
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        try {
            Page<FileInfo> result = fileStorageService.listFiles(page, pageSize);
            Map<String, Object> data = new HashMap<>();
            data.put("list", result.getRecords());
            data.put("total", result.getTotal());
            data.put("page", result.getCurrent());
            data.put("pageSize", result.getSize());
            return ApiResponse.ok(data);
        } catch (RuntimeException e) {
            log.warn("获取文件列表失败: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 下载/预览文件（按记录 ID）。
     */
    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        FileInfo fileInfo = fileStorageService.getById(id);
        Resource resource = fileStorageService.loadFileAsResource(id);

        String encodedName = URLEncoder.encode(fileInfo.getOriginalName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        String contentType = fileInfo.getContentType() != null
                ? fileInfo.getContentType()
                : "application/octet-stream";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + encodedName
                                + "\"; filename*=UTF-8''" + encodedName)
                .body(resource);
    }

    /**
     * 删除文件（按记录 ID）。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        try {
            boolean deleted = fileStorageService.delete(id);
            if (deleted) {
                return ApiResponse.ok(null, "删除成功");
            } else {
                return ApiResponse.badRequest("文件记录不存在");
            }
        } catch (RuntimeException e) {
            log.warn("文件删除失败: {}", e.getMessage());
            return ApiResponse.badRequest(e.getMessage());
        }
    }
}
