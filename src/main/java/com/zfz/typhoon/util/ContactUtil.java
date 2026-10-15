package com.zfz.typhoon.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 联系方式（问卷 / 反馈里的手机号、邮箱）的<b>加密存储与展示脱敏</b>工具。
 *
 * <h3>为什么需要它（取代了早先的「脱敏入库」）</h3>
 * 早先 {@code PasswordUtil#maskContact} 在<b>入库前</b>就把手机号改写成
 * {@code 138****8888}。那是<b>不可逆</b>的，意味着反馈一旦落库，
 * 提交人再也联系不上——"收集联系方式"这件事本身就失效了。
 * 目标场景是「<b>要能回访</b>」，因此改为：
 * <ul>
 *   <li><b>入库时加密</b>：{@link #encrypt(String)} 产出 AES-GCM 密文；</li>
 *   <li><b>读取时解密</b>：{@link #decrypt(String)} 还原原文，供有权限的人回访；</li>
 *   <li><b>展示时脱敏</b>：{@link #mask(String)} 只用于界面渲染，
 *       原文与脱敏串是两个字段，UI 默认显示脱敏串。</li>
 * </ul>
 *
 * <h3>算法与格式</h3>
 * <b>AES-256-GCM</b>（{@code AES/GCM/NoPadding}），认证加密——
 * 密文被篡改会在解密时直接失败，而不是安静地解出一段垃圾。
 * 存储格式为可识别的带前缀串：
 * <pre>
 * enc1:&lt;Base64(IV(12B) || 密文 || 认证标签(16B))&gt;
 * </pre>
 * 带 {@code enc1:} 前缀有两个好处：一是能一眼区分"密文"与"历史明文"，
 * 二是将来换算法时可以再加 {@code enc2:} 做渐进迁移。
 *
 * <h3>密钥</h3>
 * 来自 {@code db.properties} 的 {@code contact.cipher.key}（参数化，符合
 * 课程"配置需可参数化"的要求），经 SHA-256 派生出 256 位密钥，
 * 因此配置里写多长的口令都可以。配置缺失时回落到内置的开发密钥并打一条
 * WARNING ——保证演示能跑起来，同时让"生产必须配置"这件事在日志里可见。
 *
 * <h3>兼容历史数据</h3>
 * {@link #decrypt(String)} 遇到<b>不以 {@code enc1:} 开头</b>的值时原样返回。
 * 老库里存的可能是明文，也可能是早先脱敏后的 {@code 138****8888}——
 * 两种都不该让页面炸掉，前者还能用，后者至少要能照常显示。
 *
 * @author 追风者项目组
 */
public final class ContactUtil {

    private static final Logger LOG = Logger.getLogger(ContactUtil.class.getName());

    /** 密文前缀（算法版本号） */
    public static final String PREFIX = "enc1:";

    /** GCM 推荐的 IV 长度（字节） */
    private static final int IV_BYTES = 12;

    /** GCM 认证标签长度（位） */
    private static final int TAG_BITS = 128;

    /** 配置项键名 */
    private static final String CONFIG_KEY = "contact.cipher.key";

    /**
     * 开发用兜底密钥。
     * <p><b>仅供本机演示</b>。生产部署必须在 {@code db.properties} 里配
     * {@code contact.cipher.key}，否则换台机器部署会解不开已有密文。
     */
    private static final String DEV_FALLBACK_KEY = "zfz-typhoon-contact-dev-key";

    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile SecretKeySpec key;
    private static volatile boolean fallbackWarned = false;

    private ContactUtil() {
        // 工具类禁止实例化
    }

    /* ------------------------------------------------------------------ */
    /*  加解密                                                              */
    /* ------------------------------------------------------------------ */

    /**
     * 加密联系方式。
     *
     * @param plain 原始联系方式；null / 空白返回 null（"没填"就是没填，不落空串）
     * @return 形如 {@code enc1:...} 的密文
     */
    public static String encrypt(String plain) {
        if (plain == null || plain.trim().isEmpty()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.trim().getBytes(StandardCharsets.UTF_8));

            byte[] packed = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(cipherText, 0, packed, iv.length, cipherText.length);
            return PREFIX + Base64.getEncoder().encodeToString(packed);
        } catch (Exception e) {
            // 加密失败不能"退回明文存库"——那等于静默放弃保护。
            // 抛出去让调用方感知，由上层决定是重试还是拒绝本次提交。
            throw new BusinessException(Result.CODE_SERVER_ERROR,
                    "联系方式加密失败：" + e.getMessage(), e);
        }
    }

    /**
     * 解密联系方式。
     *
     * @param stored 入库值：{@code enc1:...} 密文，或历史明文/脱敏串
     * @return 原文；入参为空返回 null；密文无法解密时返回 null 并记日志
     */
    public static String decrypt(String stored) {
        if (stored == null || stored.trim().isEmpty()) {
            return null;
        }
        String value = stored.trim();
        if (!value.startsWith(PREFIX)) {
            // 历史数据：明文或早先的脱敏串，原样返回（不要试图"修复"它）
            return value;
        }
        try {
            byte[] packed = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            if (packed.length <= IV_BYTES) {
                LOG.log(Level.WARNING, "联系方式密文长度异常，无法解密");
                return null;
            }
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(packed, 0, iv, 0, IV_BYTES);
            byte[] cipherText = new byte[packed.length - IV_BYTES];
            System.arraycopy(packed, IV_BYTES, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 换过密钥、密文被改坏都会走到这里。返回 null 而不是抛异常：
            // 一条联系方式解不开，不该让整个反馈列表 500。
            LOG.log(Level.WARNING, "联系方式解密失败（密钥变更或密文损坏）：" + e.getMessage());
            return null;
        }
    }

    /**
     * 判断一个入库值是否为密文。
     *
     * @param stored 入库值
     * @return 是密文返回 true
     */
    public static boolean isEncrypted(String stored) {
        return stored != null && stored.trim().startsWith(PREFIX);
    }

    /* ------------------------------------------------------------------ */
    /*  展示脱敏                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * 展示级脱敏（<b>只用于界面渲染，不要拿它的输出去覆盖原文</b>）。
     *
     * <ul>
     *   <li>11 位手机号 → 保留前 3 后 4：{@code 138****8888}；</li>
     *   <li>邮箱 → 保留首字符与域名：{@code u***@example.com}；</li>
     *   <li>其它形态 → Base64（纯混淆，非加密，避免整串直接暴露）。</li>
     * </ul>
     *
     * @param plain 原始联系方式（<b>传解密后的原文</b>，不要传密文）
     * @return 脱敏串；入参为空返回 null
     */
    public static String mask(String plain) {
        if (plain == null || plain.trim().isEmpty()) {
            return null;
        }
        String c = plain.trim();
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

    /* ------------------------------------------------------------------ */
    /*  密钥                                                                */
    /* ------------------------------------------------------------------ */

    /**
     * 取（惰性初始化的）AES 密钥。
     *
     * @return 256 位 AES 密钥
     */
    private static SecretKeySpec secretKey() {
        SecretKeySpec k = key;
        if (k != null) {
            return k;
        }
        synchronized (ContactUtil.class) {
            if (key == null) {
                String configured = DBUtil.getConfig(CONFIG_KEY, null);
                if (configured == null || configured.trim().isEmpty()) {
                    if (!fallbackWarned) {
                        fallbackWarned = true;
                        LOG.log(Level.WARNING, "未配置 " + CONFIG_KEY
                                + "，联系方式加密回落到内置开发密钥。生产部署必须显式配置，"
                                + "否则更换机器后已有密文将无法解密。");
                    }
                    configured = DEV_FALLBACK_KEY;
                }
                key = new SecretKeySpec(sha256(configured.trim()), "AES");
            }
            return key;
        }
    }

    /**
     * 把任意长度的配置口令派生成 32 字节密钥。
     *
     * @param text 配置值
     * @return 32 字节
     */
    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(Result.CODE_SERVER_ERROR, "当前 JVM 不支持 SHA-256", e);
        }
    }
}
