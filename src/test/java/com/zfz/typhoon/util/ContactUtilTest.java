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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ContactUtil} 单元测试。
 *
 * <h3>为什么这个类值得单独测</h3>
 * 联系方式是问卷 / 反馈里唯一的个人身份数据，它的三种语义必须同时成立、
 * 且不能互相串台：
 * <ol>
 *   <li><b>可回访</b>——密文必须能解回原文（否则"收集联系方式"这件事白做了）；</li>
 *   <li><b>不落明文</b>——密文里不能直接出现原文（否则加密白做了）；</li>
 *   <li><b>能展示</b>——脱敏串只用于界面，且不能反过来破坏原文。</li>
 * </ol>
 * 另外，老库里的明文/历史脱敏串必须仍然能正常读出，不能让页面炸掉。
 *
 * @author 追风者项目组
 */
@DisplayName("ContactUtil · 联系方式加密与脱敏")
class ContactUtilTest {

    @Nested
    @DisplayName("加密与解密")
    class Cipher {

        @Test
        @DisplayName("手机号可原样解回，且密文带 enc1: 前缀、不含明文")
        void roundTripMobile() {
            String plain = "13800138000";
            String stored = ContactUtil.encrypt(plain);

            assertTrue(stored.startsWith(ContactUtil.PREFIX), "密文应有版本前缀，实际：" + stored);
            assertTrue(ContactUtil.isEncrypted(stored));
            assertFalse(stored.contains(plain), "密文里不应能直接看到原文");
            assertEquals(plain, ContactUtil.decrypt(stored));
        }

        @Test
        @DisplayName("邮箱与中文内容同样可原样解回（UTF-8 往返）")
        void roundTripEmailAndChinese() {
            String email = "user@example.com";
            String chinese = "紧急联系：张三 13800138000";

            assertEquals(email, ContactUtil.decrypt(ContactUtil.encrypt(email)));
            assertEquals(chinese, ContactUtil.decrypt(ContactUtil.encrypt(chinese)));
        }

        @Test
        @DisplayName("长联系方式也能容纳（列宽放宽到 255 的依据）")
        void roundTripLongContact() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 60; i++) {
                sb.append('a');
            }
            sb.append("@example.com");
            String plain = sb.toString();

            String stored = ContactUtil.encrypt(plain);
            assertEquals(plain, ContactUtil.decrypt(stored));
            assertTrue(stored.length() < 255, "密文长度应仍在 varchar(255) 内，实际：" + stored.length());
        }

        @Test
        @DisplayName("同一联系方式两次加密结果不同（IV 随机），但都能解回原文")
        void randomIvPerEncryption() {
            String plain = "13800138000";
            String a = ContactUtil.encrypt(plain);
            String b = ContactUtil.encrypt(plain);

            assertNotEquals(a, b, "GCM 每次应换一个随机 IV，否则相同明文产生相同密文");
            assertEquals(plain, ContactUtil.decrypt(a));
            assertEquals(plain, ContactUtil.decrypt(b));
        }

        @Test
        @DisplayName("密文被篡改时解密失败返回 null，而不是抛异常或解出垃圾")
        void tamperedCipherTextFailsSafely() {
            String stored = ContactUtil.encrypt("13800138000");
            // 改动密文正文的第一个字符：它落在随机 IV 里，
            // GCM 会因认证标签校验失败而拒绝解密（确定性行为，不依赖填充位）。
            int idx = ContactUtil.PREFIX.length();
            char c = stored.charAt(idx);
            String tampered = stored.substring(0, idx) + (c == 'A' ? 'B' : 'A')
                    + stored.substring(idx + 1);

            assertNull(ContactUtil.decrypt(tampered));
        }

        @Test
        @DisplayName("密文长度异常（只有前缀）不抛异常，返回 null")
        void truncatedCipherFailsSafely() {
            assertNull(ContactUtil.decrypt(ContactUtil.PREFIX));
            assertNull(ContactUtil.decrypt(ContactUtil.PREFIX + "AAAA"));
        }

        @Test
        @DisplayName("null / 空串加密得到 null；解密 null 得到 null")
        void nullAndBlank() {
            assertNull(ContactUtil.encrypt(null));
            assertNull(ContactUtil.encrypt(""));
            assertNull(ContactUtil.encrypt("   "));
            assertNull(ContactUtil.decrypt(null));
            assertNull(ContactUtil.decrypt("   "));
        }

        @Test
        @DisplayName("首尾空白先裁剪再加密")
        void trimsBeforeEncrypt() {
            assertEquals("13800138000", ContactUtil.decrypt(ContactUtil.encrypt("  13800138000  ")));
        }

        @Test
        @DisplayName("历史值（无 enc1: 前缀的明文或老脱敏串）原样返回")
        void legacyValuesPassThrough() {
            assertEquals("138****8000", ContactUtil.decrypt("138****8000"));
            assertEquals("someone@example.com", ContactUtil.decrypt("someone@example.com"));
            assertFalse(ContactUtil.isEncrypted("138****8000"));
        }
    }

    @Nested
    @DisplayName("展示脱敏")
    class Mask {

        @Test
        @DisplayName("11 位手机号保留前 3 位与后 4 位")
        void masksMobile() {
            assertEquals("138****5678", ContactUtil.mask("13812345678"));
        }

        @Test
        @DisplayName("邮箱保留首字符与整个域名")
        void masksEmail() {
            assertEquals("u***@example.com", ContactUtil.mask("user@example.com"));
        }

        @Test
        @DisplayName("其它形态退化为 Base64 混淆（可逆，仅避免整串直出）")
        void fallsBackToBase64() {
            assertEquals(Base64.getEncoder().encodeToString("abc".getBytes(StandardCharsets.UTF_8)),
                    ContactUtil.mask("abc"));
        }

        @Test
        @DisplayName("以 @ 开头的畸形输入不越界")
        void atSignAtStartIsSafe() {
            assertEquals(Base64.getEncoder().encodeToString("@x.com".getBytes(StandardCharsets.UTF_8)),
                    ContactUtil.mask("@x.com"));
        }

        @Test
        @DisplayName("null / 空串 / 纯空白返回 null")
        void blankReturnsNull() {
            assertNull(ContactUtil.mask(null));
            assertNull(ContactUtil.mask(""));
            assertNull(ContactUtil.mask("   "));
        }

        @Test
        @DisplayName("脱敏是纯函数：拿它去覆盖原文不会发生（原文可多次解出同一结果）")
        void maskIsPure() {
            String stored = ContactUtil.encrypt("13812345678");
            String masked = ContactUtil.mask(ContactUtil.decrypt(stored));

            assertEquals("138****5678", masked);
            assertEquals("13812345678", ContactUtil.decrypt(stored),
                    "脱敏不应影响存储的密文");
        }
    }
}
