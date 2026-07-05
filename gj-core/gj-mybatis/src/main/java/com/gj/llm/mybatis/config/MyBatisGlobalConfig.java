package com.gj.llm.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 全局配置 —— 补充自动配置未覆盖的部分。
 *
 * <p>DataSource、SqlSessionFactory、SqlSessionTemplate、TransactionManager
 * 由 Spring Boot 自动配置（{@code DataSourceAutoConfiguration} +
 * {@code MybatisPlusAutoConfiguration}）管理，此类仅负责：
 * <ul>
 *   <li>Mapper 接口统一扫描（替代在每个接口上标注 {@code @Mapper}）</li>
 *   <li>分页插件注册</li>
 *   <li>自动填充（createdAt / updatedAt）</li>
 * </ul>
 *
 * @author gj-llm
 */
@Configuration
@MapperScan("com.gj.llm.admin.mapper")
public class MyBatisGlobalConfig {

    // ==================== 分页插件 ====================

    /**
     * MyBatis-Plus 拦截器 —— 注册分页插件（MySQL 方言）。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setOverflow(true);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    // ==================== 自动填充 ====================

    /**
     * 自动填充处理器 —— INSERT 时填充 createdAt/updatedAt，UPDATE 时填充 updatedAt。
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
                this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
