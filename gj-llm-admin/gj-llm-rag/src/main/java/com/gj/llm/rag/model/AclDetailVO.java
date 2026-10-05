package com.gj.llm.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 知识库共享设置详情 VO —— 可见性 + 授权列表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AclDetailVO {

    /** 可见性：PUBLIC=全员可见 / RESTRICTED=仅 owner+授权主体可见 */
    private String visibility;

    /** 授权列表 */
    private List<AclGrantVO> grants;

    /** 当前用户是否可管理共享设置（owner/管理员；前端据此禁用写控件，后端仍强校验） */
    private Boolean canManage;
}
