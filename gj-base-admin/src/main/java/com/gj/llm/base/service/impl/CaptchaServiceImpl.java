package com.gj.llm.base.service.impl;

import com.gj.llm.base.config.CaptchaProperties;
import com.gj.llm.base.model.CaptchaResponse;
import com.gj.llm.base.service.CaptchaService;
import com.gj.llm.base.util.SlideCaptchaGenerator;
import com.gj.llm.redis.constant.CacheConstants;
import com.gj.llm.redis.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * 滑动验证码实现 —— Redis 存缺口坐标，校验 |slideX - targetX| ≤ 容差。
 *
 * <p>key 设计（{@link CacheConstants}）：{@code captcha:slide:{token}}（目标 x，TTL=有效期）、
 * {@code captcha:fail:{token}}（失败计数）、{@code captcha:gen:{ip}}（生成限流）。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService {

    private final CaptchaProperties properties;
    private final RedisService redisService;

    @Override
    public CaptchaResponse generate(String clientIp) {
        // 开关关闭：返回 enabled=false，前端隐藏滑块、登录不校验（开关唯一真源在后端）
        if (!properties.isEnabled()) {
            return CaptchaResponse.builder().enabled(false).build();
        }

        // 生成限流：单 IP 每分钟（increment 后首次写 TTL，防遗留无 TTL key）
        String genKey = CacheConstants.CAPTCHA_GEN_KEY + clientIp;
        long count = redisService.increment(genKey);
        if (count == 1) {
            redisService.expire(genKey, Duration.ofSeconds(60));
        }
        if (count > properties.getGenLimitPerMinute()) {
            throw new RuntimeException("验证码获取过于频繁，请稍后再试");
        }

        SlideCaptchaGenerator.SlidePuzzle puzzle;
        try {
            puzzle = SlideCaptchaGenerator.generate();
        } catch (IOException e) {
            log.warn("滑动验证码生成失败: {}", e.getMessage());
            throw new RuntimeException("验证码生成失败，请稍后再试");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        redisService.set(CacheConstants.CAPTCHA_SLIDE_KEY + token, puzzle.targetX(),
                Duration.ofSeconds(properties.getExpireSeconds()));

        return CaptchaResponse.builder()
                .enabled(true)
                .captchaToken(token)
                .bgImage(puzzle.bgBase64())
                .puzzleImage(puzzle.puzzleBase64())
                .puzzleY(puzzle.canvasY())
                .width(puzzle.width())
                .height(puzzle.height())
                .build();
    }

    @Override
    public void verify(String captchaToken, Integer slideX) {
        // 开关关闭：登录不校验
        if (!properties.isEnabled()) {
            return;
        }
        if (captchaToken == null || captchaToken.isBlank() || slideX == null) {
            throw new RuntimeException("请完成滑块验证");
        }

        String slideKey = CacheConstants.CAPTCHA_SLIDE_KEY + captchaToken;
        String failKey = CacheConstants.CAPTCHA_FAIL_KEY + captchaToken;
        Integer targetX = redisService.get(slideKey, Integer.class);
        if (targetX == null) {
            throw new RuntimeException("验证码已过期，请刷新后重试");
        }

        if (Math.abs(slideX - targetX) <= properties.getTolerancePx()) {
            // 验证成功即消费（单次有效），失败计数一并清除
            redisService.delete(slideKey);
            redisService.delete(failKey);
            return;
        }

        long fails = redisService.increment(failKey);
        redisService.expire(failKey, Duration.ofSeconds(properties.getExpireSeconds()));
        if (fails >= properties.getMaxAttempts()) {
            // 达到尝试上限：作废令牌，前端需刷新验证码
            redisService.delete(slideKey);
            throw new RuntimeException("滑块验证失败次数过多，请刷新验证码");
        }
        throw new RuntimeException("滑块位置不正确，请重试");
    }
}
