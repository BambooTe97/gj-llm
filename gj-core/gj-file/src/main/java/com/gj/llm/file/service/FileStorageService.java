package com.gj.llm.file.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gj.llm.file.model.FileInfo;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务接口 —— 定义文件上传、查询、下载、删除操作。
 *
 * @author gj-llm
 */
public interface FileStorageService {

    /**
     * 上传单个文件到本地磁盘，并写入数据库记录。
     *
     * @param file 上传的文件
     * @return 文件信息（包含数据库 ID）
     */
    FileInfo upload(MultipartFile file);

    /**
     * 分页查询文件记录。
     *
     * @param page     当前页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    Page<FileInfo> listFiles(int page, int pageSize);

    /**
     * 根据记录 ID 加载文件为 Spring Resource（用于下载）。
     *
     * @param id 数据库记录 ID
     * @return 文件资源
     */
    Resource loadFileAsResource(Long id);

    /**
     * 根据记录 ID 获取文件信息。
     *
     * @param id 数据库记录 ID
     * @return 文件信息
     */
    FileInfo getById(Long id);

    /**
     * 删除文件（同时删除磁盘文件和数据库记录）。
     *
     * @param id 数据库记录 ID
     * @return 是否删除成功
     */
    boolean delete(Long id);
}
