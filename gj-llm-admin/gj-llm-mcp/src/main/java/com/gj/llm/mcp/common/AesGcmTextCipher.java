package com.gj.llm.mcp.common;

import com.gj.llm.common.util.StringUtils;
import com.gj.llm.mcp.config.McpProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM 文本加解密 -- 外部 MCP server 认证凭据落库前加密、连接时解密。
 *
 * <p>随机 12 字节 IV 前置密文后整体 Base64；密钥取 {@code gj.llm.mcp.crypto.secret}
 * （Base64 编码 32 字节）。未配置时降级为「启动密钥派生 + WARN」：
 * 由固定盐派生的开发密钥可保证重启后历史密文仍可解，但强度有限，生产必须显式配置。</p>
 *
 * @author gj-llm
 */
@Slf4j
@Component
public class AesGcmTextCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    /** 开发降级盐：未配置 secret 时用它派生密钥（仅保证可解性，不承诺强度） */
    private static final byte[] DEV_SALT = "gj-llm-mcp-dev-key".getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec keySpec;
    private final SecureRandom random = new SecureRandom();

    public AesGcmTextCipher(McpProperties properties) {
        this.keySpec = resolveKey(properties.getCrypto().getSecret());
    }

    /** 加密：明文 -> Base64(iv + 密文) */
    public String encrypt(String plain) {
        if (StringUtils.isEmpty(plain)) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("凭据加密失败", e);
        }
    }

    /** 解密：Base64(iv + 密文) -> 明文；入参非本组件密文（如明文残留）时原样返回由上层校验 */
    public String decrypt(String encoded) {
        if (StringUtils.isEmpty(encoded)) {
            return encoded;
        }
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            if (all.length <= IV_LENGTH) {
                return encoded;
            }
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, all, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("凭据解密失败（密钥变更或密文非法），按原值处理");
            return encoded;
        }
    }

    /** 密钥解析：优先配置的 Base64 32 字节；否则 SHA-256(开发盐) 派生并 WARN */
    private SecretKeySpec resolveKey(String secret) {
        try {
            if (StringUtils.isNotBlank(secret)) {
                byte[] key = Base64.getDecoder().decode(secret);
                if (key.length != 32) {
                    throw new IllegalArgumentException("gj.llm.mcp.crypto.secret 须为 Base64 编码的 32 字节（256 位）密钥");
                }
                return new SecretKeySpec(key, "AES");
            }
            log.warn("未配置 gj.llm.mcp.crypto.secret，外部服务凭据使用开发派生密钥加密，生产环境必须显式配置！");
            byte[] key = MessageDigest.getInstance("SHA-256").digest(DEV_SALT);
            return new SecretKeySpec(key, "AES");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("MCP 加密密钥初始化失败", e);
        }
    }
}
