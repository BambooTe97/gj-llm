package com.gj.llm.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gj.llm.mybatis.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 文件上传记录实体 —— 映射 {@code file_record} 表。
 *
 * <p>审计字段（{@code createBy / updateBy / createdAt / updatedAt}）
 * 继承自 {@link BaseEntity}，由 {@code MetaObjectHandler} 自动填充。</p>
 *
 * <p>主键使用雪花算法（ASSIGN_ID），自动生成全局唯一 ID。</p>
 *
 * @author gj-llm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("file_record")
public class FileEntity extends BaseEntity {

    /** 主键 ID（雪花算法自动生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 原始文件名 */
    private String originalName;

    /** 存储文件名（UUID 重命名，避免冲突） */
    private String storedName;

    /** 文件扩展名（小写，不含点号） */
    private String extension;

    /** 文件大小（字节） */
    private Long size;

    /** MIME 类型 */
    private String contentType;

    /** 相对上传目录的文件路径（如 yyyy/MM/dd/storedName） */
    private String filePath;
}
