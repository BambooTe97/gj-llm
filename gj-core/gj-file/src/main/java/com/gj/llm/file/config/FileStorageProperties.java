package com.gj.llm.file.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 文件存储配置属性 —— 映射 {@code application.yml} 中 {@code app.file} 前缀的配置。
 *
 * @author gj-llm
 */
@Slf4j
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.file")
public class FileStorageProperties implements InitializingBean {

    /** 文件存储根目录（支持相对/绝对路径） */
    private String uploadDir = "./uploads";

    /** 前端访问文件的 URL 前缀（Spring MVC 资源映射 + Vite 代理） */
    private String servePath = "/files";

    /** 单个文件最大大小（字节），默认 10MB */
    private long maxFileSize = 10 * 1024 * 1024L;

    /** 允许的文件扩展名列表（小写，不含点号） */
    private List<String> allowedExtensions = List.of(
            "pdf", "doc", "docx", "txt", "md",
            "png", "jpg", "jpeg", "gif", "csv",
            "xls", "xlsx", "ppt", "pptx"
    );

    /**
     * 初始化时解析上传目录为绝对路径，避免依赖 JVM 工作目录导致文件写入错误位置。
     */
    @Override
    public void afterPropertiesSet() {
        Path path = Paths.get(uploadDir);
        if (!path.isAbsolute()) {
            path = path.toAbsolutePath().normalize();
            uploadDir = path.toString();
        }
        // 确保 servePath 以 / 开头且不以 / 结尾
        if (!servePath.startsWith("/")) {
            servePath = "/" + servePath;
        }
        if (servePath.endsWith("/")) {
            servePath = servePath.substring(0, servePath.length() - 1);
        }
        log.info("文件存储配置 -> 存储目录: {}, 访问路径: {}/*", uploadDir, servePath);
    }
}
