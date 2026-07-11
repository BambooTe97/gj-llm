package com.gj.llm.chat.model;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 重命名会话请求。
 *
 * @author gj-llm
 */
@Data
public class RenameRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 新标题 */
    private String title;
}
