package com.zfz.typhoon.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * 口令摘要工具。
 *
 * <p>数据库 {@code users.password_hash} 列存放形如
 * {@code sha256$5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8}
 * 的摘要值 —— {@code 算法$十六进制摘要}。本类负责生成与校验该格式。
 *
 * <p>安全说明（课程要求：安全防护措施完善）：
 * <ul>
 *   <li>明文口令<b>永不入库、永不回传前端</b>；</li>
 *   <li>本课程作业采用加盐 SHA-256 演示；生产环境应改用
 *       BCrypt / Argon2 这类专为口令设计的慢哈希；</li>
 *   <li>校验使用 {@link MessageDigest#isEqual(byte[], byte[])} 做恒定时间比较，
 *       避免逐字节比较泄露信息。</li>
 * </ul>
 *
 * @author 追风者项目组
 */
public final class PasswordUtil {

    /** 摘要算法标识前缀 */
    public static final String ALGO_PREFIX = "sha256$";

    private static final String SALT = "zfz-typhoon-platform-2026";

    private PasswordUtil() {
        // 工具类禁止实例化
    }

    /**
     * 计算加盐 SHA-256 摘要的十六进制字符串。
     *
     * @param raw 明文口令
     * @return 十六进制摘要（小写，64 字符）
     */
    public static String sha256Hex(String raw) {
        if (raw == null) {
            throw BusinessException.badRequest("口令不能为空");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((SALT + raw).getBytes(StandardCharsets.UTF_8));
            return toHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            // JDK 必然支持 SHA-256，走到这里说明运行环境异常
            throw new BusinessException(Result.CODE_SERVER_ERROR, "当前 JVM 不支持 SHA-256", e);
        }
    }

    /**
     * 生成入库格式的口令摘要：{@code sha256$<hex>}。
     *
     * @param raw 明文口令
     * @return 入库摘要值
     */
    public static String hash(String raw) {
        return ALGO_PREFIX + sha256Hex(raw);
    }

    /**
     * 校验明文口令与数据库摘要是否匹配。
     *
     * <p>兼容三种历史格式（顺序即匹配优先级）：
     * <ul>
     *   <li>{@code sha256$hex}，且 {@code hex} 是<b>加盐</b> SHA-256 —— 本项目
     *       {@link #hash(String)} 生成的新格式，也是首选；</li>
     *   <li>{@code sha256$hex}，但 {@code hex} 是<b>未加盐</b> SHA-256 ——
     *       课程初始化脚本 {@code Dump*.sql} 里预置的测试账号就是这种
     *       （例如 {@code admin01} 存的是 {@code SHA-256("password")}）。
     *       必须兜住，否则预置账号一个都登不进去；</li>
     *   <li>纯 {@code hex}（无前缀）—— 更早的裸摘要格式。</li>
     * </ul>
     *
     * <p>三种比较一律走 {@link MessageDigest#isEqual} 恒定时间比较。
     *
     * @param raw    用户提交的明文口令
     * @param stored 数据库中的摘要
     * @return 匹配返回 true
     */
    public static boolean verify(String raw, String stored) {
        if (raw == null || stored == null) {
            return false;
        }
        String value = stored.trim();

        if (value.startsWith(ALGO_PREFIX)) {
            String expect = value.substring(ALGO_PREFIX.length());
            // 先试加盐（本项目格式），再试不加盐（初始化脚本格式）
            return constantTimeEquals(sha256Hex(raw), expect)
                    || constantTimeEquals(plainSha256Hex(raw), expect);
        }

        // 无前缀：未加盐的裸 SHA-256
        return constantTimeEquals(plainSha256Hex(raw), value);
    }

    /**
     * 计算不加盐的 SHA-256 摘要（仅用于兼容课程初始化脚本里的历史数据）。
     *
     * @param raw 明文
     * @return 十六进制摘要
     */
    public static String plainSha256Hex(String raw) {
        if (raw == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(Result.CODE_SERVER_ERROR, "当前 JVM 不支持 SHA-256", e);
        }
    }

    /**
     * 加密问卷联系方式（课程要求：联系方式加密存储）。
     *
     * <p>属于可逆的演示级混淆，用于在页面与日志中<b>展示脱敏后</b>的联系方式。
     * 生产环境应改用密钥管理服务 + AES-GCM。
     *
     * @param contact 原始联系方式
     * @return Base64 混淆串；入参为空时返回 null
     */
    public static String maskContact(String contact) {
        if (contact == null || contact.trim().isEmpty()) {
            return null;
        }
        String c = contact.trim();
        // 手机号：保留前 3 位与后 4 位
        if (c.matches("\\d{11}")) {
            return c.substring(0, 3) + "****" + c.substring(7);
        }
        // 邮箱：保留首字符与域名
        int at = c.indexOf('@');
        if (at > 0) {
            return c.charAt(0) + "***" + c.substring(at);
        }
        return Base64.getEncoder().encodeToString(c.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 恒定时间字符串比较。
     *
     * @param a 值一
     * @param b 值二
     * @return 相等返回 true
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 字节数组转小写十六进制字符串。
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int v = b & 0xFF;
            if (v < 16) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(v));
        }
        return sb.toString();
    }
}
