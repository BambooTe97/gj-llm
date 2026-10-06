package com.gj.llm.mcp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * MCP 工具调用审计日志实体 -- 双向工具调用全量审计，由监听器异步落库。
 *
 * <p>日志表不继承 BaseEntity（异步线程无安全上下文，create_by 自动填充无意义），
 * createdAt 由监听器手动赋值（与 chat MessageEntity 同做法）。</p>
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("mcp_audit_log")
public class McpAuditLogEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 调用方向：SERVER_CALLED / CLIENT_CALL */
    private String direction;

    /** 调用方用户 ID */
    private Long userId;

    /** 调用方用户名（快照） */
    private String username;

    /** server 被调时的 API Key ID */
    private Long apiKeyId;

    /** client 出调时的目标 server 名 */
    private String serverName;

    /** 工具名 */
    private String toolName;

    /** 入参摘要（超长截断） */
    private String paramsDigest;

    /** 结果状态：SUCCESS / ERROR / TIMEOUT */
    private String resultStatus;

    /** 失败原因 */
    private String errorMessage;

    /** 耗时（毫秒） */
    private Long costMs;

    /** 来源 IP */
    private String clientIp;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
