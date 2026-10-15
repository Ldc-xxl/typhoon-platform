package com.zfz.typhoon.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PasswordUtil} 单元测试。
 *
 * <p>这是全站唯一的口令闸门，出错只有两种后果：把正确的人挡在外面，
 * 或把错误的人放进来。因此这里覆盖三件事：
 * <ol>
 *   <li><b>摘要算法本身</b>——用外部可复算的已知哈希值钉死，
 *       改动 SALT / 迭代方式会立刻被这几条断言拦住；</li>
 *   <li><b>三种历史存储格式的兼容</b>——本项目加盐格式、课程初始化脚本的
 *       不加盐格式、更早的裸 hex 格式，都必须能校验通过，
 *       否则预置账号整批登不进去；</li>
 *   <li><b>空值与不匹配</b>——不能因为 null 就抛异常或放行。</li>
 * </ol>
 *
 * <p>已知哈希值（外部算好，写死在断言里，避免"用被测方法验被测方法"）：
 * <ul>
 *   <li>加盐 SHA-256("password") = {@code 4766c48d…7b0281}</li>
 *   <li>不加盐 SHA-256("password") = {@code 5e884898…1542d8}</li>
 *   <li>不加盐 SHA-256("123456") = {@code 8d969eef…c6c92}</li>
 * </ul>
 */
@DisplayName("PasswordUtil · 口令摘要")
class PasswordUtilTest {

    /** 本项目加盐格式：SHA-256(SALT + "password") */
    private static final String SALTED_PASSWORD =
            "4766c48d8761ec5d9de86d5609b32349744c73c4762389a6295a65e8447b0281";

    /** 课程初始化脚本里的不加盐格式：SHA-256("password") */
    private static final String PLAIN_PASSWORD =
            "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8";

    /** 不加盐 SHA-256("123456") */
    private static final String PLAIN_123456 =
            "8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92";

    @Nested
    @DisplayName("摘要计算")
    class Digest {

        @Test
        @DisplayName("历史全局盐摘要与外部算好的已知值一致（保证存量账号仍可校验）")
        void legacyGlobalSaltDigestMatchesKnownValue() {
            assertEquals(SALTED_PASSWORD, PasswordUtil.sha256Hex("password"));
        }

        @Test
        @DisplayName("hash() 产出 sha256$<32位盐>$<64位摘要> 的三段格式")
        void hashProducesSaltedStorageFormat() {
            String stored = PasswordUtil.hash("password");
            String[] parts = stored.split("\\$");

            assertEquals(3, parts.length, "应为 算法$盐$摘要 三段，实际：" + stored);
            assertEquals("sha256", parts[0]);
            assertEquals(PasswordUtil.SALT_HEX_LENGTH, parts[1].length(), "盐应为 32 个十六进制字符");
            assertTrue(parts[1].matches("[0-9a-f]{32}"), "盐应为小写十六进制");
            assertTrue(parts[2].matches("[0-9a-f]{64}"), "摘要正文应为 64 位小写十六进制");
            assertEquals("sha256$".length() + PasswordUtil.SALT_HEX_LENGTH + 1 + 64, stored.length());
        }

        @Test
        @DisplayName("每个口令一份随机盐：同一口令两次 hash 必不相同，但都能校验通过")
        void saltIsRandomPerCall() {
            String a = PasswordUtil.hash("password");
            String b = PasswordUtil.hash("password");

            assertNotEquals(a, b, "随机盐必须让同一口令产生不同摘要，否则等于没加盐");
            assertTrue(PasswordUtil.verify("password", a));
            assertTrue(PasswordUtil.verify("password", b));
        }

        @Test
        @DisplayName("不加盐摘要与外部算好的已知值一致")
        void plainDigestMatchesKnownValue() {
            assertEquals(PLAIN_PASSWORD, PasswordUtil.plainSha256Hex("password"));
            assertEquals(PLAIN_123456, PasswordUtil.plainSha256Hex("123456"));
        }

        @Test
        @DisplayName("全局盐摘要与不加盐摘要必须不同（否则历史盐没生效）")
        void saltActuallyChangesDigest() {
            assertFalse(PasswordUtil.sha256Hex("password")
                    .equals(PasswordUtil.plainSha256Hex("password")));
        }

        @Test
        @DisplayName("sha256Hex 传 null 抛 400；plainSha256Hex 传 null 返回空串")
        void nullHandling() {
            assertEquals(Result.CODE_BAD_REQUEST,
                    assertThrows(BusinessException.class,
                            () -> PasswordUtil.sha256Hex(null)).getCode());
            assertEquals("", PasswordUtil.plainSha256Hex(null));
        }

        @Test
        @DisplayName("hash 传 null 抛 400，不会生成一个「空口令」的合法摘要")
        void hashRejectsNull() {
            assertEquals(Result.CODE_BAD_REQUEST,
                    assertThrows(BusinessException.class,
                            () -> PasswordUtil.hash(null)).getCode());
        }

        @Test
        @DisplayName("大小写与中文口令都能得到稳定、可区分的结果")
        void handlesCaseAndChinese() {
            assertEquals(64, PasswordUtil.sha256Hex("追风者").length());
            assertTrue(PasswordUtil.verify("追风者", PasswordUtil.hash("追风者")));
            assertFalse(PasswordUtil.verify("追风者", PasswordUtil.hash("追風者")));
            assertFalse(PasswordUtil.sha256Hex("password").equals(PasswordUtil.sha256Hex("Password")),
                    "口令应区分大小写");
        }
    }

    @Nested
    @DisplayName("verify · 四种存储格式")
    class Verify {

        @Test
        @DisplayName("当前随机盐格式（本项目 hash() 产物）校验通过")
        void acceptsSaltedFormat() {
            assertTrue(PasswordUtil.verify("password", PasswordUtil.hash("password")));
        }

        @Test
        @DisplayName("历史加全局盐格式校验通过")
        void acceptsLegacyGlobalSaltFormat() {
            assertTrue(PasswordUtil.verify("password",
                    PasswordUtil.ALGO_PREFIX + SALTED_PASSWORD));
        }

        @Test
        @DisplayName("不加盐格式（课程预置账号）校验通过")
        void acceptsPlainSha256Format() {
            assertTrue(PasswordUtil.verify("password",
                    PasswordUtil.ALGO_PREFIX + PLAIN_PASSWORD));
            assertTrue(PasswordUtil.verify("123456",
                    PasswordUtil.ALGO_PREFIX + PLAIN_123456));
        }

        @Test
        @DisplayName("裸 hex（无前缀）格式校验通过")
        void acceptsBareHexFormat() {
            assertTrue(PasswordUtil.verify("password", PLAIN_PASSWORD));
        }

        @Test
        @DisplayName("摘要首尾空白会被裁掉后再比对")
        void trimsStoredValue() {
            assertTrue(PasswordUtil.verify("password",
                    "  " + PasswordUtil.ALGO_PREFIX + SALTED_PASSWORD + "  "));
        }

        @Test
        @DisplayName("口令错误一律不通过")
        void rejectsWrongPassword() {
            assertFalse(PasswordUtil.verify("wrong", PasswordUtil.hash("password")));
            assertFalse(PasswordUtil.verify("Password", PasswordUtil.hash("password")));
            assertFalse(PasswordUtil.verify("", PasswordUtil.hash("password")));
            assertFalse(PasswordUtil.verify("password", PasswordUtil.hash("password2")));
        }

        @Test
        @DisplayName("入参含 null 时返回 false 而不是抛异常")
        void nullSafe() {
            assertFalse(PasswordUtil.verify(null, PasswordUtil.hash("password")));
            assertFalse(PasswordUtil.verify("password", null));
            assertFalse(PasswordUtil.verify(null, null));
        }

        @Test
        @DisplayName("摘要比对是字节级精确匹配，大小写十六进制不等价")
        void digestComparisonIsByteExact() {
            // 记录当前行为：本工程所有摘要均以 hash() 生成且为小写，
            // 因此该差异在真实数据中不会出现；此处仅钉死语义，避免被误认为大小写不敏感。
            assertFalse(PasswordUtil.verify("password",
                    PasswordUtil.ALGO_PREFIX + SALTED_PASSWORD.toUpperCase()));
        }

        @Test
        @DisplayName("残缺摘要不会误判为通过")
        void rejectsMalformedStored() {
            assertFalse(PasswordUtil.verify("password", PasswordUtil.ALGO_PREFIX));
            assertFalse(PasswordUtil.verify("password", PasswordUtil.ALGO_PREFIX + "abc"));
            assertFalse(PasswordUtil.verify("password", PasswordUtil.ALGO_PREFIX + "abcd$"));
        }
    }

    @Nested
    @DisplayName("needsUpgrade · 渐进迁移判定")
    class NeedsUpgrade {

        @Test
        @DisplayName("当前随机盐格式无需升级")
        void currentFormatNeedsNoUpgrade() {
            assertFalse(PasswordUtil.needsUpgrade(PasswordUtil.hash("password")));
        }

        @Test
        @DisplayName("历史格式（全局盐 / 不加盐 / 裸 hex）都判为待升级")
        void legacyFormatsNeedUpgrade() {
            assertTrue(PasswordUtil.needsUpgrade(PasswordUtil.ALGO_PREFIX + SALTED_PASSWORD));
            assertTrue(PasswordUtil.needsUpgrade(PasswordUtil.ALGO_PREFIX + PLAIN_PASSWORD));
            assertTrue(PasswordUtil.needsUpgrade(PLAIN_PASSWORD));
        }

        @Test
        @DisplayName("空值不判为待升级（避免对空摘要反复写库）")
        void blankNeedsNoUpgrade() {
            assertFalse(PasswordUtil.needsUpgrade(null));
            assertFalse(PasswordUtil.needsUpgrade(""));
            assertFalse(PasswordUtil.needsUpgrade("   "));
        }
    }

    @Nested
    @DisplayName("maskContact · 联系方式脱敏")
    class MaskContact {

        @Test
        @DisplayName("11 位手机号保留前 3 位与后 4 位")
        void masksMobile() {
            assertEquals("138****5678", PasswordUtil.maskContact("13812345678"));
        }

        @Test
        @DisplayName("邮箱保留首字符与整个域名部分")
        void masksEmail() {
            assertEquals("u***@example.com", PasswordUtil.maskContact("user@example.com"));
        }

        @Test
        @DisplayName("其它形态退化为 Base64 混淆")
        void fallsBackToBase64() {
            assertEquals(Base64.getEncoder().encodeToString("abc".getBytes(StandardCharsets.UTF_8)),
                    PasswordUtil.maskContact("abc"));
        }

        @Test
        @DisplayName("以 @ 开头的畸形输入也走 Base64 分支，不越界")
        void atSignAtStartIsSafe() {
            assertEquals(Base64.getEncoder().encodeToString("@x.com".getBytes(StandardCharsets.UTF_8)),
                    PasswordUtil.maskContact("@x.com"));
        }

        @Test
        @DisplayName("null / 空串 / 纯空白返回 null")
        void blankReturnsNull() {
            assertNull(PasswordUtil.maskContact(null));
            assertNull(PasswordUtil.maskContact(""));
            assertNull(PasswordUtil.maskContact("   "));
        }

        @Test
        @DisplayName("首尾空白先裁剪再脱敏")
        void trimsBeforeMasking() {
            assertEquals("138****5678", PasswordUtil.maskContact("  13812345678  "));
        }
    }
}
