package com.gj.llm.file.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 文件静态资源映射配置 —— 将 YAML 中配置的 {@code serve-path} URL 路径
 * 映射到本地 {@code upload-dir} 磁盘目录，前端可直接通过 URL 访问上传文件。
 *
 * <p>例如：{@code /files/2026/07/05/uuid.pdf} → {@code ./uploads/2026/07/05/uuid.pdf}</p>
 *
 * @author gj-llm
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class FileResourceConfig implements WebMvcConfigurer {

    private final FileStorageProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String urlPattern = properties.getServePath() + "/**";
        String resourceLocation = "file:" + properties.getUploadDir() + "/";

        registry.addResourceHandler(urlPattern)
                .addResourceLocations(resourceLocation);

        log.info("静态资源映射: {} → {}", urlPattern, properties.getUploadDir());
    }
}
