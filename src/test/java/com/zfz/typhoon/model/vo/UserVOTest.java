package com.zfz.typhoon.model.vo;

import com.zfz.typhoon.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UserVO#of(User, LocalDateTime)} 的分支测试。
 *
 * <p>该工厂方法同时承担三件事：字段映射、角色/状态的文案推导、操作按钮的组装。
 * 它输出错一处，管理后台就会多一个点不动或点错的按钮，或把停用账号显示成正常。
 * 因此这里按<b>角色 × 状态 × 时间入参</b>把每个分支都钉死。
 *
 * @author 追风者项目组
 */
@DisplayName("UserVO · 用户视图对象")
class UserVOTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2024, 2, 1, 10, 30, 0);

    /* ------------------------------------------------------------------ */
    /*  测试数据构造                                                        */
    /* ------------------------------------------------------------------ */

    private static User user(String username, String nickname, String role, Boolean status) {
        User u = new User();
        u.setUserId(7L);
        u.setUsername(username);
        u.setNickname(nickname);
        u.setEmail("a@b.com");
        u.setPhone("13800000000");
        u.setRole(role);
        u.setStatus(status);
        u.setCreatedAt(CREATED);
        return u;
    }

    @Nested
    @DisplayName("of · 空入参")
    class NullInput {

        @Test
        @DisplayName("user 为 null 时返回空 VO，所有字段均为 null")
        void nullUserYieldsEmptyVo() {
            UserVO vo = UserVO.of(null, LocalDateTime.of(2026, 9, 1, 0, 0));

            assertNull(vo.getId());
            assertNull(vo.getAccount());
            assertNull(vo.getNickname());
            assertNull(vo.getEmail());
            assertNull(vo.getPhone());
            assertNull(vo.getRole());
            assertNull(vo.getRoleCode());
            assertNull(vo.getScope());
            assertNull(vo.getRegistered());
            assertNull(vo.getLastLogin());
            assertNull(vo.getStatus());
            assertNull(vo.getEnabled());
            assertNull(vo.getAvatarText());
            assertNull(vo.getOps());
        }
    }

    @Nested
    @DisplayName("of · 角色与权限文案")
    class RoleAndScope {

        @Test
        @DisplayName("管理员：角色=平台总管理员，范围=全部功能 + 系统配置，操作=编辑 · 重置")
        void admin() {
            UserVO vo = UserVO.of(user("alice", "艾丽丝", "ADMIN", Boolean.TRUE), null);

            assertEquals("平台总管理员", vo.getRole());
            assertEquals("全部功能 + 系统配置", vo.getScope());
            assertEquals("编辑 · 重置", vo.getOps());
            assertEquals("ADMIN", vo.getRoleCode());
        }

        @Test
        @DisplayName("普通用户：角色=普通用户，范围=查询 / 沙盘 / 问卷，操作=编辑 · 禁用")
        void normalUser() {
            UserVO vo = UserVO.of(user("bob", "鲍勃", "USER", Boolean.TRUE), null);

            assertEquals("普通用户", vo.getRole());
            assertEquals("查询 / 沙盘 / 问卷", vo.getScope());
            assertEquals("编辑 · 禁用", vo.getOps());
            assertEquals("USER", vo.getRoleCode());
        }
    }

    @Nested
    @DisplayName("of · 状态文案与操作按钮")
    class StatusText {

        @Test
        @DisplayName("status=true：状态显示「正常」，操作提供「禁用」")
        void enabled() {
            UserVO vo = UserVO.of(user("bob", "鲍勃", "USER", Boolean.TRUE), null);

            assertEquals("正常", vo.getStatus());
            assertEquals(Boolean.TRUE, vo.getEnabled());
            assertEquals("编辑 · 禁用", vo.getOps());
        }

        @Test
        @DisplayName("status=false：状态显示「已停用」，操作改为「启用」")
        void disabled() {
            UserVO vo = UserVO.of(user("bob", "鲍勃", "USER", Boolean.FALSE), null);

            assertEquals("已停用", vo.getStatus());
            assertEquals(Boolean.FALSE, vo.getEnabled());
            assertEquals("编辑 · 启用", vo.getOps());
        }

        @Test
        @DisplayName("status=null：按「正常」处理，操作仍是「禁用」")
        void nullStatusTreatedAsEnabled() {
            UserVO vo = UserVO.of(user("bob", "鲍勃", "USER", null), null);

            assertEquals("正常", vo.getStatus());
            assertNull(vo.getEnabled(), "enabled 应如实回填 null，而不是替客户端编一个值");
            assertEquals("编辑 · 禁用", vo.getOps());
        }

        @Test
        @DisplayName("管理员即使 status=false，操作也不给「停用」（管理员账号不允许停用）")
        void adminNeverGetsDisableAction() {
            UserVO vo = UserVO.of(user("root", "管理员", "ADMIN", Boolean.FALSE), null);

            assertEquals("编辑 · 重置", vo.getOps());
            assertEquals("已停用", vo.getStatus(), "状态文案仍如实反映数据");
        }
    }

    @Nested
    @DisplayName("of · 时间字段与回退规则")
    class TimeFields {

        @Test
        @DisplayName("createdAt 为 null 时 registered 为 null")
        void nullCreatedAt() {
            User u = user("bob", "鲍勃", "USER", Boolean.TRUE);
            u.setCreatedAt(null);

            assertNull(UserVO.of(u, null).getRegistered());
        }

        @Test
        @DisplayName("createdAt 非空时 registered 取注册日期（yyyy-MM-dd）")
        void formattedCreatedAt() {
            assertEquals("2024-02-01",
                    UserVO.of(user("bob", "鲍勃", "USER", Boolean.TRUE), null).getRegistered());
        }

        @Test
        @DisplayName("lastLogin 入参为 null 时回退到实体的 lastLogin")
        void fallsBackToEntityLastLogin() {
            User u = user("bob", "鲍勃", "USER", Boolean.TRUE);
            u.setLastLogin(LocalDateTime.of(2026, 9, 20, 21, 30, 0));

            assertEquals("2026-09-20 21:30:00", UserVO.of(u, null).getLastLogin());
        }

        @Test
        @DisplayName("lastLogin 入参与实体都为空时结果为 null，不伪造时间")
        void bothNull() {
            User u = user("bob", "鲍勃", "USER", Boolean.TRUE);
            u.setLastLogin(null);

            assertNull(UserVO.of(u, null).getLastLogin());
        }

        @Test
        @DisplayName("lastLogin 入参非空时优先使用它，而不是实体的字段")
        void explicitLastLoginWins() {
            User u = user("bob", "鲍勃", "USER", Boolean.TRUE);
            u.setLastLogin(LocalDateTime.of(2020, 1, 1, 0, 0, 0));

            assertEquals("2026-09-20 21:30:00",
                    UserVO.of(u, LocalDateTime.of(2026, 9, 20, 21, 30, 0)).getLastLogin());
        }
    }

    @Nested
    @DisplayName("of · 头像字符回退")
    class AvatarText {

        @Test
        @DisplayName("有昵称时取昵称首字符")
        void nicknameFirst() {
            assertEquals("追",
                    UserVO.of(user("bob", "追风少年", "USER", Boolean.TRUE), null).getAvatarText());
        }

        @Test
        @DisplayName("昵称为 null 时回退到用户名首字符")
        void nicknameNullFallsBackToUsername() {
            assertEquals("b",
                    UserVO.of(user("bob", null, "USER", Boolean.TRUE), null).getAvatarText());
        }

        @Test
        @DisplayName("昵称为空串时回退到用户名首字符")
        void nicknameEmptyFallsBackToUsername() {
            assertEquals("b",
                    UserVO.of(user("bob", "", "USER", Boolean.TRUE), null).getAvatarText());
        }

        @Test
        @DisplayName("昵称与用户名都为空时用 \"?\" 兜底")
        void bothBlank() {
            assertEquals("?",
                    UserVO.of(user(null, null, "USER", Boolean.TRUE), null).getAvatarText());
        }

        @Test
        @DisplayName("用户名为 null、昵称为空串时也用 \"?\" 兜底")
        void blankNicknameAndNullUsername() {
            assertEquals("?",
                    UserVO.of(user(null, "", "USER", Boolean.TRUE), null).getAvatarText());
        }
    }

    @Nested
    @DisplayName("of · 字段映射与安全性")
    class FieldMapping {

        @Test
        @DisplayName("基本字段逐一映射")
        void mapsBasicFields() {
            UserVO vo = UserVO.of(user("bob", "鲍勃", "USER", Boolean.TRUE), null);

            assertEquals(Long.valueOf(7L), vo.getId());
            assertEquals("bob", vo.getAccount());
            assertEquals("鲍勃", vo.getNickname());
            assertEquals("a@b.com", vo.getEmail());
            assertEquals("13800000000", vo.getPhone());
        }

        @Test
        @DisplayName("VO 不携带口令字段，且 toString 不含摘要")
        void neverCarriesPasswordHash() throws Exception {
            User u = user("bob", "鲍勃", "USER", Boolean.TRUE);
            u.setPasswordHash("sha256$deadbeef");

            UserVO vo = UserVO.of(u, null);

            assertFalse(vo.toString().contains("sha256$"),
                    "toString 不得含口令摘要，实际：" + vo.toString());

            // 从类型结构上确认 VO 根本没有口令相关字段（比字符串断言更强）
            for (Field f : UserVO.class.getDeclaredFields()) {
                String n = f.getName().toLowerCase();
                assertFalse(n.contains("password") || n.contains("hash"),
                        "UserVO 不应存在口令相关字段，发现：" + f.getName());
            }
            assertNotNull(vo, "VO 正常构建");
        }
    }
}
