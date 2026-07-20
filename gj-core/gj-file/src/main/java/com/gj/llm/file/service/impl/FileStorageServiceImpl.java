package com.gj.llm.file.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gj.llm.file.config.FileStorageProperties;
import com.gj.llm.file.constant.FileTypeEnum;
import com.gj.llm.file.entity.FileEntity;
import com.gj.llm.file.mapper.FileMapper;
import com.gj.llm.file.model.FileInfo;
import com.gj.llm.file.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 文件存储服务实现 —— 文件存本地磁盘、元数据存数据库。
 *
 * <p>文件存储路径结构：{@code {uploadDir}/yyyy/MM/dd/{uuid}_{originalFilename}}</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageServiceImpl implements FileStorageService {

    private final FileStorageProperties properties;
    private final FileMapper fileMapper;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    @Override
    @Transactional
    public FileInfo upload(MultipartFile file) {
        // 1. 校验
        validateFile(file);
        String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("unknown");
        String extension = getExtension(originalName);
        validateExtension(extension);

        // 2. 生成存储文件名和路径
        String storedName = UUID.randomUUID().toString() + (extension.isEmpty() ? "" : "." + extension);
        String datePath = LocalDateTime.now().format(DATE_FORMAT);
        Path uploadDir = Paths.get(properties.getUploadDir(), datePath);

        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            log.error("创建上传目录失败: {}", uploadDir, e);
            throw new RuntimeException("创建上传目录失败");
        }

        // 3. 写入磁盘
        Path targetPath = uploadDir.resolve(storedName);
        try {
            file.transferTo(targetPath);
            log.info("文件写入磁盘成功: {}", targetPath);
        } catch (IOException e) {
            log.error("文件写入失败: {}", targetPath, e);
            throw new RuntimeException("文件写入失败");
        }

        // 4. 写入数据库记录（审计字段由 MetaObjectHandler 自动填充）
        String relativePath = datePath + "/" + storedName;

        FileEntity entity = FileEntity.builder()
                .originalName(originalName)
                .storedName(storedName)
                .extension(extension)
                .size(file.getSize())
                .contentType(file.getContentType())
                .filePath(relativePath)
                .build();

        fileMapper.insert(entity);
        log.info("文件记录写入数据库成功: id={}, name={}", entity.getId(), originalName);

        // 5. 返回 FileInfo
        return toFileInfo(entity);
    }

    @Override
    public Page<FileInfo> listFiles(int page, int pageSize) {
        // 按创建时间倒序分页查询
        IPage<FileEntity> entityPage = fileMapper.selectPage(
                new Page<>(page, pageSize),
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FileEntity>()
                        .orderByDesc(FileEntity::getCreatedAt)
        );

        List<FileInfo> list = entityPage.getRecords().stream()
                .map(this::toFileInfo)
                .toList();

        Page<FileInfo> result = new Page<>(page, pageSize);
        result.setRecords(list);
        result.setTotal(entityPage.getTotal());
        return result;
    }

    @Override
    public FileInfo getById(Long id) {
        FileEntity entity = fileMapper.selectById(id);
        if (entity == null) {
            throw new RuntimeException("文件记录不存在: id=" + id);
        }
        return toFileInfo(entity);
    }

    @Override
    public Resource loadFileAsResource(Long id) {
        FileEntity entity = fileMapper.selectById(id);
        if (entity == null) {
            throw new RuntimeException("文件记录不存在: id=" + id);
        }

        Path filePath = Paths.get(properties.getUploadDir()).resolve(entity.getFilePath()).normalize();

        // 安全检查
        Path uploadDirPath = Paths.get(properties.getUploadDir()).toAbsolutePath().normalize();
        if (!filePath.toAbsolutePath().normalize().startsWith(uploadDirPath)) {
            throw new RuntimeException("非法的文件路径");
        }

        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("文件不存在或不可读: " + entity.getOriginalName());
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("文件路径无效");
        }
    }

    @Override
    @Transactional
    public boolean delete(Long id) {
        FileEntity entity = fileMapper.selectById(id);
        if (entity == null) {
            return false;
        }

        // 1. 删除数据库记录
        fileMapper.deleteById(id);

        // 2. 删除磁盘文件
        Path filePath = Paths.get(properties.getUploadDir()).resolve(entity.getFilePath()).normalize();
        try {
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("磁盘文件删除成功: {}", filePath);
                cleanEmptyDirs(filePath.getParent());
            } else {
                log.warn("磁盘文件不存在: {}", filePath);
            }
        } catch (IOException e) {
            log.error("磁盘文件删除失败: {}", filePath, e);
            // DB 记录已删除，磁盘删除失败仅记录日志
        }

        return true;
    }

    // ==================== 私有辅助方法 ====================

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("上传文件为空");
        }
        if (file.getSize() > properties.getMaxFileSize()) {
            throw new RuntimeException("文件大小超出限制，最大允许 "
                    + properties.getMaxFileSize() / 1024 / 1024 + "MB");
        }
    }

    private void validateExtension(String extension) {
        if (extension.isEmpty()) return;
        // 优先使用配置的白名单，未配置则使用 FileTypeEnum
        List<String> allowed = properties.getAllowedExtensions();
        if (allowed != null && !allowed.isEmpty()) {
            if (!allowed.contains(extension.toLowerCase())) {
                throw new RuntimeException("不支持的文件类型: ." + extension
                        + "，允许的类型: " + String.join(", ", allowed));
            }
        } else if (FileTypeEnum.fromExtension(extension).isEmpty()) {
            throw new RuntimeException("不支持的文件类型: ." + extension
                    + "，允许的类型: " + String.join(", ", FileTypeEnum.allExtensions()));
        }
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) return "";
        return filename.substring(dotIndex + 1).toLowerCase();
    }

    /** Entity → FileInfo 转换 */
    private FileInfo toFileInfo(FileEntity entity) {
        return FileInfo.builder()
                .id(entity.getId())
                .storedName(entity.getStoredName())
                .originalName(entity.getOriginalName())
                .extension(entity.getExtension())
                .size(entity.getSize())
                .contentType(entity.getContentType())
                .createBy(entity.getCreateBy())
                .url("/api/files/" + entity.getId())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    /** 递归清理空父目录 */
    private void cleanEmptyDirs(Path dir) {
        try {
            Path uploadDir = Paths.get(properties.getUploadDir()).toAbsolutePath().normalize();
            Path current = dir.toAbsolutePath().normalize();
            while (!current.equals(uploadDir) && current.startsWith(uploadDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(current)) {
                    if (stream.iterator().hasNext()) break;
                }
                Files.deleteIfExists(current);
                current = current.getParent();
            }
        } catch (IOException e) {
            log.debug("清理空目录时出错（可忽略）: {}", e.getMessage());
        }
    }
}
