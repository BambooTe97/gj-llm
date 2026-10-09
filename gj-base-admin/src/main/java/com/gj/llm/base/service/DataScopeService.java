package com.gj.llm.base.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gj.llm.base.entity.UserEntity;
import com.gj.llm.base.model.DataScope;

import java.util.List;

/**
 * 数据权限服务 -- 解析当前登录用户的角色数据域（五档），为各查询点提供过滤原语。
 *
 * <p>选型为服务层手工过滤（非 MP DataPermissionInterceptor / XML 改写），对齐项目
 * 全 LambdaQueryWrapper 风格；作用点：用户列表（sys_user.dept_id/.id）、操作日志与
 * 登录日志（user_id，经 {@link #resolveScopedUserIds}）。</p>
 *
 * @author gj-llm
 */
public interface DataScopeService {

    /**
     * 解析当前登录用户的数据权限域（多角色取并集，最宽生效；管理员角色绕过）。
     *
     * @return 数据权限域；未登录/无角色返回空域（fail-closed，不报错）
     */
    DataScope resolve();

    /**
     * 为 sys_user 查询追加数据域过滤条件。
     *
     * <p>注意：必须在关键字 OR 组之前调用（AND 语义）。all 域不加条件；
     * 空域追加 {@code 1=0}（结果为空）；deptIds/selfUserIds 并存时以 OR 组合并后 AND 进查询。</p>
     *
     * @param wrapper 用户查询构造器
     */
    void applyUserScope(LambdaQueryWrapper<UserEntity> wrapper);

    /**
     * 解析当前用户可见的 userId 集合（供无部门列的日志表按 user_id 过滤）。
     *
     * @return all 域返回 {@code null}（表示不过滤）；空域返回空列表；否则返回可见用户 ID
     */
    List<Long> resolveScopedUserIds();
}
