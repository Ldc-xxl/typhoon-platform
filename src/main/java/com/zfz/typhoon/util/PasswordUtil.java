package com.zfz.typhoon.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * 口令摘要工具。
 *
 * <p>数据库 {@code users.password_hash} 列存放形如
 * {@code sha256$3f1a9c…$5e884898…} 的摘要值 ——
 * {@code 算法$十六进制盐$十六进制摘要}。本类负责生成与校验这些格式。
 *
 * <h3>盐：每个口令一份，随机生成（2026-10-11 修正）</h3>
 * 早先 {@code SALT} 是一个硬编码的全局常量。那<b>不是 salt，是 pepper</b>：
 * 全库同口令得到同一摘要，攻击者只需针对这一个固定串做一次爆破，
 * 结果即可套用到所有用户；彩虹表虽然被挡住，但"一次爆破、全体生效"的
 * 放大效应依然存在。现在每个口令用 {@link SecureRandom} 生成独立的
 * {@value #SALT_HEX_LENGTH} 位十六进制盐并随摘要一起存储，
 * 同口令不同用户摘要必然不同，爆破必须逐个用户重来。
 *
 * <h3>格式与兼容（四种，顺序即匹配优先级）</h3>
 * <ol>
 *   <li>{@code sha256$salt$hex} —— <b>当前格式</b>，{@link #hash(String)} 的产物；</li>
 *   <li>{@code sha256$hex} —— 历史格式：{@code hex} 是加全局 pepper 的摘要；</li>
 *   <li>{@code sha256$hex} —— 课程初始化脚本 {@code Dump*.sql} 的预置账号：
 *       {@code hex} 是<b>未加盐</b>摘要（例如 {@code admin01} 存
 *       {@code SHA-256("password")}）。必须兜住，否则预置账号一个都登不进去；</li>
 *   <li>纯 {@code hex}（无前缀）—— 更早的裸摘要格式。</li>
 * </ol>
 * 2—4 属于历史数据，登录成功时由 {@code AuthServiceImpl} 顺手升级为格式 1，
 * 见 {@link #needsUpgrade(String)}。
 *
 * <p>安全说明（课程要求：安全防护措施完善）：
 * <ul>
 *   <li>明文口令<b>永不入库、永不回传前端</b>；</li>
 *   <li>本课程作业采用<b>随机盐 SHA-256</b> 演示；生产环境应改用
 *       BCrypt / Argon2 这类专为口令设计的<b>慢哈希</b>——
 *       单轮 SHA-256 即使用上随机盐，单机每秒仍可试算上亿次；</li>
 *   <li>所有摘要比对一律走 {@link MessageDigest#isEqual(byte[], byte[])}
 *       做恒定时间比较，避免逐字节比较泄露信息。</li>
 * </ul>
 *
 * @author 追风者项目组
 */
public final class PasswordUtil {

    /** 摘要算法标识前缀 */
    public static final String ALGO_PREFIX = "sha256$";

    /** 新格式里盐与摘要的分隔符 */
    private static final char SALT_SEPARATOR = '$';

    /** 随机盐的十六进制长度（16 字节 → 32 个 hex 字符） */
    static final int SALT_HEX_LENGTH = 32;

    /**
     * 历史全局盐（本质是 pepper）。
     * <p><b>仅用于校验与升级早期数据</b>，{@link #hash(String)} 已不再使用它。
     * 改名而不是删除，是为了让"这个常量为什么还在"这个问题有据可查。
     */
    private static final String LEGACY_SALT = "zfz-typhoon-platform-2026";

    /** 盐用密码学安全随机源 */
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {
        // 工具类禁止实例化
    }

    /**
     * 生成一个新的十六进制随机盐。
     *
     * @return {@value #SALT_HEX_LENGTH} 位小写十六进制串
     */
    public static String newSalt() {
        byte[] bytes = new byte[SALT_HEX_LENGTH / 2];
        RANDOM.nextBytes(bytes);
        return toHex(bytes);
    }

    /**
     * 计算<b>加全局 pepper</b> 的 SHA-256 摘要的十六进制字符串。
     *
     * <p><b>历史格式专用</b>：只用于校验早期入库的口令。新口令请用
     * {@link #hash(String)}（随机盐）。保留本方法也因为它的输出被
     * {@code PasswordUtilTest} 用外部算好的已知值钉死——
     * 一旦有人改动这里的实现，历史账号会整批登不进去。
     *
     * @param raw 明文口令
     * @return 十六进制摘要（小写，64 字符）
     */
    public static String sha256Hex(String raw) {
        if (raw == null) {
            throw BusinessException.badRequest("口令不能为空");
        }
        return hexOf(LEGACY_SALT + raw);
    }

    /**
     * 用指定盐计算摘要：{@code SHA-256(salt + "$" + raw)} 的十六进制。
     *
     * <p>盐本身是 hex，不可能含 {@code $}，因此分隔符不会被误切分；
     * 加上分隔符是为了让"盐 + 口令"的边界在算法层面无歧义
     * （否则 {@code salt="ab", raw="c"} 与 {@code salt="a", raw="bc"} 会碰撞）。
     *
     * @param saltHex 十六进制盐
     * @param raw     明文口令
     * @return 十六进制摘要（小写，64 字符）
     */
    private static String digest(String saltHex, String raw) {
        return hexOf(saltHex + SALT_SEPARATOR + raw);
    }

    /**
     * 生成入库格式的口令摘要：{@code sha256$<随机盐>$<摘要>}。
     *
     * @param raw 明文口令
     * @return 入库摘要值
     */
    public static String hash(String raw) {
        if (raw == null) {
            throw BusinessException.badRequest("口令不能为空");
        }
        String salt = newSalt();
        return ALGO_PREFIX + salt + SALT_SEPARATOR + digest(salt, raw);
    }

    /**
     * 校验明文口令与数据库摘要是否匹配。
     *
     * <p>兼容四种历史格式（见类注释），顺序即匹配优先级。
     * 所有比较一律走 {@link MessageDigest#isEqual} 恒定时间比较。
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
            String rest = value.substring(ALGO_PREFIX.length());
            int sep = rest.indexOf(SALT_SEPARATOR);
            if (sep > 0) {
                // 当前格式：sha256$<salt>$<hex>，用随摘要存储的盐复算
                String salt = rest.substring(0, sep);
                String expect = rest.substring(sep + 1);
                return constantTimeEquals(digest(salt, raw), expect);
            }
            // 历史格式 sha256$<hex>：先试加全局 pepper，再试不加盐
            return constantTimeEquals(sha256Hex(raw), rest)
                    || constantTimeEquals(plainSha256Hex(raw), rest);
        }

        // 无前缀：未加盐的裸 SHA-256
        return constantTimeEquals(plainSha256Hex(raw), value);
    }

    /**
     * 判断一条已入库的摘要是否需要升级为当前的随机盐格式。
     *
     * <p>用于<b>渐进迁移</b>：老账号不必强制改密，登录成功时顺手重算一次即可。
     * 之所以不能在校验时就地重写，是因为校验是只读语义——
     * 在那里写库会让"验证口令"这个动作产生副作用，并发登录还会互相覆盖。
     *
     * @param stored 数据库中的摘要
     * @return 需要升级返回 true；已经是当前格式、或入参为空时返回 false
     */
    public static boolean needsUpgrade(String stored) {
        if (stored == null || stored.trim().isEmpty()) {
            return false;
        }
        String value = stored.trim();
        if (!value.startsWith(ALGO_PREFIX)) {
            return true;                                   // 裸 hex 格式
        }
        String rest = value.substring(ALGO_PREFIX.length());
        return rest.indexOf(SALT_SEPARATOR) <= 0;           // 无盐的单摘要
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
        return hexOf(raw);
    }

    /**
     * 对问卷 / 反馈的联系方式做<b>展示级脱敏</b>。
     *
     * <p><b>注意：这不是加密。</b>实现已迁到 {@link ContactUtil#mask(String)}
     * ——联系方式相关的加解密与脱敏都归 {@code ContactUtil}，本方法仅为
     * 兼容既有调用点与测试而保留，语义等价。
     *
     * @param contact 原始联系方式
     * @return 脱敏串；入参为空时返回 null
     * @see ContactUtil#mask(String)
     */
    public static String maskContact(String contact) {
        return ContactUtil.mask(contact);
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
     * 对字符串做 UTF-8 SHA-256 并转小写十六进制。
     *
     * @param text 原文
     * @return 十六进制摘要（64 字符）
     */
    private static String hexOf(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // JDK 必然支持 SHA-256，走到这里说明运行环境异常
            throw new BusinessException(Result.CODE_SERVER_ERROR, "当前 JVM 不支持 SHA-256", e);
        }
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
