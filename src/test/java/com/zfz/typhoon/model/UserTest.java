package com.zfz.typhoon.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link User} 的行为测试。
 *
 * <p>{@code User} 虽是实体，但带两个非平凡点：角色判定的<b>大小写不敏感</b>，
 * 以及 {@link User#toString()} 的脱敏输出。前者决定鉴权走向，
 * 后者决定日志里会不会出现异常内容，都值得单测钉死。
 *
 * @author 追风者项目组
 */
@DisplayName("User · 用户实体")
class UserTest {

    @Nested
    @DisplayName("isAdmin · 角色判定")
    class IsAdmin {

        @Test
        @DisplayName("role = \"ADMIN\" 时判定为管理员")
        void exactAdmin() {
            assertTrue(user("ADMIN").isAdmin());
        }

        @Test
        @DisplayName("role = \"admin\"（小写）同样判定为管理员，避免因大小写漏判")
        void lowerCaseAdmin() {
            assertTrue(user("admin").isAdmin());
        }

        @Test
        @DisplayName("role = \"Admin\"（混合大小写）同样判定为管理员")
        void mixedCaseAdmin() {
            assertTrue(user("Admin").isAdmin());
        }

        @Test
        @DisplayName("role = \"User\" 为普通用户")
        void normalUser() {
            assertFalse(user("User").isAdmin());
        }

        @Test
        @DisplayName("role = \"USER\" 为普通用户")
        void upperCaseUser() {
            assertFalse(user("USER").isAdmin());
        }

        @Test
        @DisplayName("role = null 不判为管理员（不能因缺字段而提权）")
        void nullRole() {
            assertFalse(user(null).isAdmin());
        }

        @Test
        @DisplayName("role = 空串 不判为管理员")
        void emptyRole() {
            assertFalse(user("").isAdmin());
        }

        @Test
        @DisplayName("role = \"ADMINISTRATOR\" 这类前缀相似的串不应被误判")
        void similarButNotEqual() {
            assertFalse(user("ADMINISTRATOR").isAdmin());
        }
    }

    @Nested
    @DisplayName("toString · 日志输出")
    class ToStringText {

        @Test
        @DisplayName("输出包含用户名与角色，便于排障")
        void containsUsernameAndRole() {
            User u = new User();
            u.setUsername("alice");
            u.setRole("ADMIN");

            String text = u.toString();

            assertTrue(text.contains("alice"), "应包含用户名，实际：" + text);
            assertTrue(text.contains("ADMIN"), "应包含角色，实际：" + text);
        }

        @Test
        @DisplayName("输出绝不包含口令摘要")
        void neverLeaksPasswordHash() {
            User u = new User();
            u.setUsername("alice");
            u.setRole("USER");
            u.setPasswordHash("sha256$deadbeef");

            assertFalse(u.toString().contains("sha256$"),
                    "toString 不得泄露口令摘要，实际：" + u.toString());
            assertFalse(u.toString().contains("deadbeef"),
                    "toString 不得泄露口令摘要，实际：" + u.toString());
        }

        @Test
        @DisplayName("字段全为 null 时 toString 仍可安全调用")
        void nullSafe() {
            assertEquals("User{null, role=null}", new User().toString());
        }
    }

    private static User user(String role) {
        User u = new User();
        u.setRole(role);
        return u;
    }
}
