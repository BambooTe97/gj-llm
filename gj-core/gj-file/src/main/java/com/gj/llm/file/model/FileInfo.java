package com.gj.llm.file.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文件信息响应 DTO —— 返回给前端的文件元数据。
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileInfo {

    /** 记录 ID（雪花算法） */
    private Long id;

    /** 存储文件名（UUID 生成，唯一标识） */
    private String storedName;

    /** 文件原始名称 */
    private String originalName;

    /** 文件扩展名（不含点号） */
    private String extension;

    /** 文件大小（字节） */
    private long size;

    /** 文件 MIME 类型 */
    private String contentType;

    /** 创建者（上传用户名） */
    private String createBy;

    /** 下载 URL */
    private String url;

    /** 上传时间 */
    private LocalDateTime createdAt;
}
