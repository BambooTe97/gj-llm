package com.gj.llm.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.gj.llm.common.util.SecurityUtils;
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
 *   <li>Mapper 接口统一扫描（按模块显式列出 mapper 包，新增模块需在此追加）</li>
 *   <li>分页插件注册</li>
 *   <li>自动填充（审计字段：createBy / updateBy / createdAt / updatedAt）</li>
 * </ul>
 *
 * @author gj-llm
 */
@Configuration
@MapperScan({"com.gj.llm.admin.mapper", "com.gj.llm.file.mapper"})
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
     * 自动填充处理器 —— INSERT/UPDATE 时根据 {@link com.baomidou.mybatisplus.annotation.FieldFill}
     * 自动设置审计字段值。
     *
     * <p>审计字段由 {@link com.gj.llm.mybatis.entity.BaseEntity} 统一定义，各模块实体继承即可。
     * 操作人通过 {@link SecurityUtils#getCurrentUsername()} 获取，
     * 未登录时返回 {@code "anonymous"}。</p>
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                String username = SecurityUtils.getCurrentUsername();

                this.strictInsertFill(metaObject, "createBy", String.class, username);
                this.strictInsertFill(metaObject, "updateBy", String.class, username);
                this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
                this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                String username = SecurityUtils.getCurrentUsername();

                this.strictUpdateFill(metaObject, "updateBy", String.class, username);
                this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, now);
            }
        };
    }
}
