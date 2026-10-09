package com.gj.llm.base.model;

import lombok.Builder;
import lombok.Data;

/**
 * 滑动验证码生成响应。
 *
 * <p>图片为 Base64 编码的 PNG（不含 data URI 前缀，前端自行拼接 {@code data:image/png;base64,}）。
 * 拼图块内容不含缺口位置信息——位置只能从背景暗洞目测，这是滑动验证码的机制本身。</p>
 *
 * @author gj-llm
 */
@Data
@Builder
public class CaptchaResponse {

    /** 验证码开关是否开启（false 时其余字段为空，前端隐藏滑块） */
    private boolean enabled;

    /** 一次性验证码令牌（登录时随 captchaToken + slideX 提交） */
    private String captchaToken;

    /** 背景图（Base64 PNG，含缺口暗洞） */
    private String bgImage;

    /** 拼图块（Base64 PNG，透明背景裁剪块） */
    private String puzzleImage;

    /** 拼图块纵向位置（像素，前端按此摆放拼图块 y 坐标） */
    private Integer puzzleY;

    /** 背景图宽度（像素） */
    private Integer width;

    /** 背景图高度（像素） */
    private Integer height;
}
