package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.UserDao;
import com.zfz.typhoon.model.User;
import com.zfz.typhoon.model.vo.UserVO;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.PasswordUtil;
import com.zfz.typhoon.util.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UserServiceImpl} 单元测试。
 *
 * <p>被测对象是管理后台唯一的用户写入口，因此本类重点覆盖三组行为：
 * <ol>
 *   <li><b>三套正则</b>——登录名 {@code ^[A-Za-z][A-Za-z0-9_]{3,49}$}、邮箱、手机号，
 *       合法与非法样例都要过一遍；</li>
 *   <li><b>权限自锁防护</b>——不能停用/降级/删除自己，也不能动到最后一个启用管理员；
 *       这些是权限系统里最容易留下"把自己锁在门外"缺陷的地方；</li>
 *   <li><b>字段默认值</b>——角色缺省 USER、状态缺省启用、昵称缺省回填登录名。</li>
 * </ol>
 *
 * <p>本服务的依赖全部来自带参构造注入，无静态可变状态，因此不需要跨测试清理。
 *
 * @author 追风者项目组
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl · 用户管理业务")
class UserServiceImplTest {

    @Mock
    private UserDao userDao;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userDao);
    }

    /* ------------------------------------------------------------------ */
    /*  测试数据构造与断言辅助                                               */
    /* ------------------------------------------------------------------ */

    /** 断言抛出的业务异常码与提示片段。 */
    private static void assertBusinessError(int expectedCode, String expectedPart,
                                            Runnable action) {
        BusinessException e = assertThrows(BusinessException.class, action::run);
        assertEquals(expectedCode, e.getCode(), "业务码不符：" + e.getMessage());
        assertTrue(e.getMessage() != null && e.getMessage().contains(expectedPart),
                "提示文案应包含「" + expectedPart + "」，实际：" + e.getMessage());
    }

    private static User user(long userId, String username, String role, Boolean status) {
        User u = new User();
        u.setUserId(Long.valueOf(userId));
        u.setUsername(username);
        u.setRole(role);
        u.setStatus(status);
        return u;
    }

    /** 一个只填了合法登录名的创建请求，供各分支单独补充字段。 */
    private static User createRequest() {
        User u = new User();
        u.setUsername("alice");
        return u;
    }

    /** 重复 n 次单字符，便于构造超长/同字符输入。 */
    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    /* ==================================================================== */
    /*  list                                                                */
    /* ==================================================================== */

    @Nested
    @DisplayName("list · 条件查询")
    class ListQuery {

        @Test
        @DisplayName("关键词与角色为空或纯空白时归一为 null 再查库")
        void blankFiltersBecomeNull() {
            Mockito.when(userDao.search(null, null, null))
                    .thenReturn(Collections.<User>emptyList());

            assertTrue(service.list(null, "   ", null).isEmpty());
            Mockito.verify(userDao).search(null, null, null);
        }

        @Test
        @DisplayName("关键词裁剪空白、角色转大写后再查库")
        void trimsKeywordAndUppercasesRole() {
            Mockito.when(userDao.search("飞燕", "ADMIN", Boolean.TRUE))
                    .thenReturn(Collections.<User>emptyList());

            service.list("  飞燕 ", " admin ", Boolean.TRUE);

            Mockito.verify(userDao).search("飞燕", "ADMIN", Boolean.TRUE);
        }

        @Test
        @DisplayName("角色非法（非 ADMIN/USER）抛 400，且不查库")
        void invalidRoleRejected() {
            assertBusinessError(400, "role 取值非法",
                    () -> service.list(null, "GUEST", null));
            Mockito.verifyNoInteractions(userDao);
        }

        @Test
        @DisplayName("查询结果映射为 UserVO，registered 取创建日期、lastLogin 取真实登录时间")
        void mapsRowsToVo() {
            User u = user(7L, "alice", "USER", Boolean.TRUE);
            u.setNickname("爱丽丝");
            u.setCreatedAt(LocalDateTime.of(2024, 1, 2, 3, 4, 5));
            u.setUpdatedAt(LocalDateTime.of(2026, 9, 1, 9, 0, 0));      // 改资料时间
            u.setLastLogin(LocalDateTime.of(2026, 9, 20, 21, 30, 0));   // 真正的登录时间
            Mockito.when(userDao.search(null, null, null)).thenReturn(Arrays.asList(u));

            List<UserVO> vos = service.list(null, null, null);

            assertEquals(1, vos.size());
            assertEquals(Long.valueOf(7L), vos.get(0).getId());
            assertEquals("alice", vos.get(0).getAccount());
            assertEquals("普通用户", vos.get(0).getRole());
            assertEquals("2024-01-02", vos.get(0).getRegistered());
            assertEquals("2026-09-20 21:30:00", vos.get(0).getLastLogin());
        }
    }

    /* ==================================================================== */
    /*  getById                                                             */
    /* ==================================================================== */

    @Nested
    @DisplayName("getById · 按主键查询")
    class GetById {

        @Test
        @DisplayName("用户不存在抛 404")
        void notFound() {
            Mockito.when(userDao.findById(99L)).thenReturn(null);
            assertBusinessError(404, "用户不存在", () -> service.getById(99L));
        }

        @Test
        @DisplayName("存在时返回视图对象")
        void found() {
            Mockito.when(userDao.findById(7L)).thenReturn(user(7L, "alice", "USER", Boolean.TRUE));
            assertEquals("alice", service.getById(7L).getAccount());
        }
    }

    /* ==================================================================== */
    /*  create                                                              */
    /* ==================================================================== */

    @Nested
    @DisplayName("create · 新建用户")
    class Create {

        @Test
        @DisplayName("用户信息为 null 抛 400")
        void nullUser() {
            assertBusinessError(400, "用户信息不能为空", () -> service.create(null, "abc123"));
            Mockito.verifyNoInteractions(userDao);
        }

        @Test
        @DisplayName("登录名为 null / 空串 / 纯空白一律 400")
        void blankUsername() {
            User a = new User();
            assertBusinessError(400, "登录名不能为空", () -> service.create(a, "abc123"));

            User b = new User();
            b.setUsername("   ");
            assertBusinessError(400, "登录名不能为空", () -> service.create(b, "abc123"));
            Mockito.verifyNoInteractions(userDao);
        }

        @Test
        @DisplayName("登录名不符合 ^[A-Za-z][A-Za-z0-9_]{3,49}$ 时抛 400")
        void invalidUsernamePattern() {
            String[] bad = {
                    "1abc",                      // 数字开头
                    "ab",                        // 不足 4 位
                    "abc-def",                   // 含连字符
                    repeat('a', 51)              // 超过 50 位
            };
            for (String name : bad) {
                User u = new User();
                u.setUsername(name);
                assertBusinessError(400, "登录名需以字母开头",
                        () -> service.create(u, "abc123"));
            }
            Mockito.verifyNoInteractions(userDao);
        }

        @Test
        @DisplayName("登录名合法样例（下划线、字母数字混合、边界长度）都能通过校验")
        void validUsernameSamples() {
            String[] good = {"abcd", "alice_01", "A1_b2C3", repeat('a', 50)};
            for (String name : good) {
                User u = new User();
                u.setUsername(name);
                Mockito.when(userDao.existsUsername(name)).thenReturn(false);
                Mockito.when(userDao.insert(u)).thenReturn(1L);
                assertEquals(1L, service.create(u, "abc123"));
            }
        }

        @Test
        @DisplayName("登录名已被占用抛 400，且不写库")
        void duplicateUsername() {
            User u = createRequest();
            Mockito.when(userDao.existsUsername("alice")).thenReturn(true);

            assertBusinessError(400, "登录名已被占用", () -> service.create(u, "abc123"));
            Mockito.verify(userDao, Mockito.never()).insert(Mockito.any(User.class));
        }

        @Test
        @DisplayName("邮箱格式不正确抛 400")
        void invalidEmail() {
            User u = createRequest();
            u.setEmail("not-an-email");
            Mockito.when(userDao.existsUsername("alice")).thenReturn(false);

            assertBusinessError(400, "邮箱格式不正确", () -> service.create(u, "abc123"));
        }

        @Test
        @DisplayName("手机号格式不正确抛 400")
        void invalidPhone() {
            User u = createRequest();
            u.setPhone("12345");
            Mockito.when(userDao.existsUsername("alice")).thenReturn(false);

            assertBusinessError(400, "手机号格式不正确", () -> service.create(u, "abc123"));
        }

        @Test
        @DisplayName("昵称超过 50 个字符抛 400")
        void nicknameTooLong() {
            User u = createRequest();
            u.setNickname(repeat('长', 51));
            Mockito.when(userDao.existsUsername("alice")).thenReturn(false);

            assertBusinessError(400, "昵称不能超过 50 个字符", () -> service.create(u, "abc123"));
        }

        @Test
        @DisplayName("口令为 null 或不足 6 位抛 400")
        void passwordTooShort() {
            User a = createRequest();
            Mockito.when(userDao.existsUsername("alice")).thenReturn(false);
            assertBusinessError(400, "密码长度不能少于 6 位",
                    () -> service.create(a, null));

            User b = createRequest();
            assertBusinessError(400, "密码长度不能少于 6 位",
                    () -> service.create(b, "abc12"));
        }

        @Test
        @DisplayName("口令超过 64 位抛 400")
        void passwordTooLong() {
            User u = createRequest();
            Mockito.when(userDao.existsUsername("alice")).thenReturn(false);

            assertBusinessError(400, "密码长度不能超过 64 位",
                    () -> service.create(u, repeat('a', 65)));
        }

        @Test
        @DisplayName("角色非法抛 400")
        void invalidRole() {
            User u = createRequest();
            u.setRole("GUEST");
            Mockito.when(userDao.existsUsername("alice")).thenReturn(false);

            assertBusinessError(400, "role 取值非法", () -> service.create(u, "abc123"));
        }

        @Test
        @DisplayName("合法创建：裁剪登录名、回填默认值、写入加盐摘要")
        void successDefaults() {
            User u = new User();
            u.setUsername("  alice  ");
            u.setEmail("  ");
            u.setPhone("");
            u.setNickname("   ");
            u.setRole(" user ");
            // status 保持 null
            Mockito.when(userDao.existsUsername("alice")).thenReturn(false);
            Mockito.when(userDao.insert(u)).thenReturn(42L);

            long id = service.create(u, "abc123");

            assertEquals(42L, id);
            assertEquals("alice", u.getUsername());
            assertNull(u.getEmail());
            assertNull(u.getPhone());
            assertEquals("alice", u.getNickname());
            assertEquals("USER", u.getRole());
            assertEquals(Boolean.TRUE, u.getStatus());
            assertEquals(PasswordUtil.hash("abc123"), u.getPasswordHash());
            assertFalse(u.getPasswordHash().contains("abc123"));
        }

        @Test
        @DisplayName("显式指定 ADMIN 与启用状态时保留原值")
        void explicitRoleAndStatus() {
            User u = createRequest();
            u.setRole("admin");
            u.setStatus(Boolean.FALSE);
            u.setEmail("a@b.com");
            u.setPhone("13800138000");
            u.setNickname("追风少年");
            Mockito.when(userDao.existsUsername("alice")).thenReturn(false);
            Mockito.when(userDao.insert(u)).thenReturn(1L);

            service.create(u, "abc123");

            assertEquals("ADMIN", u.getRole());
            assertEquals(Boolean.FALSE, u.getStatus());
            assertEquals("a@b.com", u.getEmail());
            assertEquals("13800138000", u.getPhone());
            assertEquals("追风少年", u.getNickname());
        }
    }

    /* ==================================================================== */
    /*  updateProfile                                                       */
    /* ==================================================================== */

    @Nested
    @DisplayName("updateProfile · 修改资料")
    class UpdateProfile {

        @Test
        @DisplayName("user 为 null 或缺少 userId 抛 400")
        void missingUserId() {
            assertBusinessError(400, "缺少 userId", () -> service.updateProfile(null));

            User noId = new User();
            assertBusinessError(400, "缺少 userId", () -> service.updateProfile(noId));
            Mockito.verifyNoInteractions(userDao);
        }

        @Test
        @DisplayName("目标用户不存在抛 404")
        void notFound() {
            User u = user(99L, "alice", "USER", Boolean.TRUE);
            Mockito.when(userDao.findById(99L)).thenReturn(null);

            assertBusinessError(404, "用户不存在", () -> service.updateProfile(u));
        }

        @Test
        @DisplayName("登录名与口令摘要被清空，资料字段可更新")
        void clearsUsernameAndHash() {
            User u = user(7L, "hacker", "ADMIN", Boolean.TRUE);   // 试图改登录名与角色
            u.setPasswordHash("sha256$deadbeef");
            u.setNickname("新昵称");
            Mockito.when(userDao.findById(7L)).thenReturn(user(7L, "alice", "USER", Boolean.TRUE));
            Mockito.when(userDao.updateProfile(u)).thenReturn(1);

            assertEquals(1, service.updateProfile(u));

            assertNull(u.getUsername());
            assertNull(u.getPasswordHash());
            assertEquals("新昵称", u.getNickname());
            Mockito.verify(userDao).updateProfile(u);
        }

        @Test
        @DisplayName("资料非法（邮箱）抛 400，且不写库")
        void invalidProfile() {
            User u = user(7L, "alice", "USER", Boolean.TRUE);
            u.setEmail("bad-email");
            Mockito.when(userDao.findById(7L)).thenReturn(user(7L, "alice", "USER", Boolean.TRUE));

            assertBusinessError(400, "邮箱格式不正确", () -> service.updateProfile(u));
            Mockito.verify(userDao, Mockito.never()).updateProfile(Mockito.any(User.class));
        }
    }

    /* ==================================================================== */
    /*  setEnabled                                                          */
    /* ==================================================================== */

    @Nested
    @DisplayName("setEnabled · 启用 / 停用")
    class SetEnabled {

        @Test
        @DisplayName("目标用户不存在抛 404")
        void notFound() {
            Mockito.when(userDao.findById(9L)).thenReturn(null);
            assertBusinessError(404, "用户不存在", () -> service.setEnabled(9L, true, 1L));
        }

        @Test
        @DisplayName("停用当前登录账号抛 403")
        void cannotDisableSelf() {
            Mockito.when(userDao.findById(7L)).thenReturn(user(7L, "alice", "USER", Boolean.TRUE));
            assertBusinessError(403, "不能停用当前登录账号",
                    () -> service.setEnabled(7L, false, 7L));
            Mockito.verify(userDao, Mockito.never()).setEnabled(Mockito.anyLong(), Mockito.anyBoolean());
        }

        @Test
        @DisplayName("停用最后一个启用管理员抛 403")
        void cannotDisableLastAdmin() {
            User admin = user(7L, "root", "ADMIN", Boolean.TRUE);
            Mockito.when(userDao.findById(7L)).thenReturn(admin);
            Mockito.when(userDao.listByRole("ADMIN")).thenReturn(Arrays.asList(admin));

            assertBusinessError(403, "至少一名启用状态的管理员",
                    () -> service.setEnabled(7L, false, 1L));
        }

        @Test
        @DisplayName("还有其它启用管理员时可以停用该管理员")
        void allowsDisablingAdminWhenOthersRemain() {
            User admin = user(7L, "root", "ADMIN", Boolean.TRUE);
            User other = user(8L, "root2", "ADMIN", Boolean.TRUE);
            Mockito.when(userDao.findById(7L)).thenReturn(admin);
            Mockito.when(userDao.listByRole("ADMIN")).thenReturn(Arrays.asList(admin, other));
            Mockito.when(userDao.setEnabled(7L, false)).thenReturn(1);

            assertEquals(1, service.setEnabled(7L, false, 1L));
        }

        @Test
        @DisplayName("已停用的管理员不构成最后一根稻草")
        void alreadyDisabledAdminSkipsGuard() {
            User admin = user(7L, "root", "ADMIN", Boolean.FALSE);
            Mockito.when(userDao.findById(7L)).thenReturn(admin);
            Mockito.when(userDao.setEnabled(7L, false)).thenReturn(1);

            assertEquals(1, service.setEnabled(7L, false, 1L));
            Mockito.verify(userDao, Mockito.never()).listByRole(Mockito.anyString());
        }

        @Test
        @DisplayName("停用普通用户不触发管理员保护，且不做 listByRole 查询")
        void disableNormalUser() {
            Mockito.when(userDao.findById(7L)).thenReturn(user(7L, "alice", "USER", Boolean.TRUE));
            Mockito.when(userDao.setEnabled(7L, false)).thenReturn(1);

            assertEquals(1, service.setEnabled(7L, false, 1L));
            Mockito.verify(userDao, Mockito.never()).listByRole(Mockito.anyString());
        }

        @Test
        @DisplayName("写库返回 0 行时抛 404")
        void zeroRowsNotFound() {
            Mockito.when(userDao.findById(7L)).thenReturn(user(7L, "alice", "USER", Boolean.TRUE));
            Mockito.when(userDao.setEnabled(7L, true)).thenReturn(0);

            assertBusinessError(404, "用户不存在", () -> service.setEnabled(7L, true, 1L));
        }
    }

    /* ==================================================================== */
    /*  setRole                                                             */
    /* ==================================================================== */

    @Nested
    @DisplayName("setRole · 调整角色")
    class SetRole {

        @Test
        @DisplayName("role 为 null 抛 400")
        void nullRole() {
            assertBusinessError(400, "缺少 role", () -> service.setRole(7L, null, 1L));
            Mockito.verifyNoInteractions(userDao);
        }

        @Test
        @DisplayName("role 取值非法抛 400")
        void invalidRole() {
            assertBusinessError(400, "role 取值非法", () -> service.setRole(7L, "GUEST", 1L));
            Mockito.verifyNoInteractions(userDao);
        }

        @Test
        @DisplayName("目标用户不存在抛 404")
        void notFound() {
            Mockito.when(userDao.findById(9L)).thenReturn(null);
            assertBusinessError(404, "用户不存在", () -> service.setRole(9L, "ADMIN", 1L));
        }

        @Test
        @DisplayName("把自己降级为普通用户抛 403")
        void cannotDowngradeSelf() {
            Mockito.when(userDao.findById(7L)).thenReturn(user(7L, "root", "ADMIN", Boolean.TRUE));
            assertBusinessError(403, "不能降低当前登录账号的权限",
                    () -> service.setRole(7L, "user", 7L));
        }

        @Test
        @DisplayName("把最后一个启用管理员降级抛 403")
        void cannotDowngradeLastAdmin() {
            User admin = user(7L, "root", "ADMIN", Boolean.TRUE);
            Mockito.when(userDao.findById(7L)).thenReturn(admin);
            Mockito.when(userDao.listByRole("ADMIN")).thenReturn(Arrays.asList(admin));

            assertBusinessError(403, "无法降级", () -> service.setRole(7L, "USER", 1L));
        }

        @Test
        @DisplayName("提升为 ADMIN 不需要管理员保护")
        void promoteToAdmin() {
            Mockito.when(userDao.findById(7L)).thenReturn(user(7L, "alice", "USER", Boolean.TRUE));
            Mockito.when(userDao.setRole(7L, "ADMIN")).thenReturn(1);

            assertEquals(1, service.setRole(7L, " admin ", 1L));
            Mockito.verify(userDao, Mockito.never()).listByRole(Mockito.anyString());
        }

        @Test
        @DisplayName("写库返回 0 行时抛 404")
        void zeroRowsNotFound() {
            Mockito.when(userDao.findById(7L)).thenReturn(user(7L, "alice", "USER", Boolean.TRUE));
            Mockito.when(userDao.setRole(7L, "ADMIN")).thenReturn(0);

            assertBusinessError(404, "用户不存在", () -> service.setRole(7L, "ADMIN", 1L));
        }
    }

    /* ==================================================================== */
    /*  resetPassword                                                       */
    /* ==================================================================== */

    @Nested
    @DisplayName("resetPassword · 重置口令")
    class ResetPassword {

        @Test
        @DisplayName("目标用户不存在抛 404")
        void notFound() {
            Mockito.when(userDao.findById(9L)).thenReturn(null);
            assertBusinessError(404, "用户不存在", () -> service.resetPassword(9L, "abc123"));
        }

        @Test
        @DisplayName("新口令过短抛 400，且不写库")
        void tooShort() {
            Mockito.when(userDao.findById(7L)).thenReturn(user(7L, "alice", "USER", Boolean.TRUE));
            assertBusinessError(400, "密码长度不能少于 6 位",
                    () -> service.resetPassword(7L, "abc"));
            Mockito.verify(userDao, Mockito.never())
                    .updatePassword(Mockito.anyLong(), Mockito.anyString());
        }

        @Test
        @DisplayName("合法重置：写入加盐摘要")
        void success() {
            Mockito.when(userDao.findById(7L)).thenReturn(user(7L, "alice", "USER", Boolean.TRUE));
            Mockito.when(userDao.updatePassword(7L, PasswordUtil.hash("newPass1"))).thenReturn(1);

            assertEquals(1, service.resetPassword(7L, "newPass1"));
            Mockito.verify(userDao).updatePassword(7L, PasswordUtil.hash("newPass1"));
        }

        @Test
        @DisplayName("写库返回 0 行时抛 404")
        void zeroRowsNotFound() {
            Mockito.when(userDao.findById(7L)).thenReturn(user(7L, "alice", "USER", Boolean.TRUE));
            Mockito.when(userDao.updatePassword(7L, PasswordUtil.hash("newPass1"))).thenReturn(0);

            assertBusinessError(404, "用户不存在", () -> service.resetPassword(7L, "newPass1"));
        }
    }

    /* ==================================================================== */
    /*  delete                                                              */
    /* ==================================================================== */

    @Nested
    @DisplayName("delete · 删除用户")
    class Delete {

        @Test
        @DisplayName("删除当前登录账号抛 403")
        void cannotDeleteSelf() {
            assertBusinessError(403, "不能删除当前登录账号",
                    () -> service.delete(7L, 7L));
            Mockito.verifyNoInteractions(userDao);
        }

        @Test
        @DisplayName("目标用户不存在抛 404")
        void notFound() {
            Mockito.when(userDao.findById(9L)).thenReturn(null);
            assertBusinessError(404, "用户不存在", () -> service.delete(9L, 1L));
        }

        @Test
        @DisplayName("删除最后一个启用管理员抛 403")
        void cannotDeleteLastAdmin() {
            User admin = user(7L, "root", "ADMIN", Boolean.TRUE);
            Mockito.when(userDao.findById(7L)).thenReturn(admin);
            Mockito.when(userDao.listByRole("ADMIN")).thenReturn(Arrays.asList(admin));

            assertBusinessError(403, "无法删除", () -> service.delete(7L, 1L));
        }

        @Test
        @DisplayName("删除普通用户成功")
        void success() {
            Mockito.when(userDao.findById(7L)).thenReturn(user(7L, "alice", "USER", Boolean.TRUE));
            Mockito.when(userDao.deleteById(7L)).thenReturn(1);

            assertEquals(1, service.delete(7L, 1L));
        }

        @Test
        @DisplayName("写库返回 0 行时抛 404")
        void zeroRowsNotFound() {
            Mockito.when(userDao.findById(7L)).thenReturn(user(7L, "alice", "USER", Boolean.TRUE));
            Mockito.when(userDao.deleteById(7L)).thenReturn(0);

            assertBusinessError(404, "用户不存在", () -> service.delete(7L, 1L));
        }
    }

    /* ==================================================================== */
    /*  stats                                                               */
    /* ==================================================================== */

    @Nested
    @DisplayName("stats · 统计")
    class Stats {

        @Test
        @DisplayName("停用数 = 总数 - 启用数")
        void counts() {
            Mockito.when(userDao.countAll()).thenReturn(10);
            Mockito.when(userDao.countByRole("ADMIN")).thenReturn(2);
            Mockito.when(userDao.countEnabled()).thenReturn(7);

            Map<String, Integer> m = service.stats();

            assertEquals(Integer.valueOf(10), m.get("total"));
            assertEquals(Integer.valueOf(2), m.get("admin"));
            assertEquals(Integer.valueOf(7), m.get("normal"));
            assertEquals(Integer.valueOf(3), m.get("disabled"));
        }

        @Test
        @DisplayName("启用数大于总数时停用数取 0，不出现负数")
        void disabledNeverNegative() {
            Mockito.when(userDao.countAll()).thenReturn(3);
            Mockito.when(userDao.countByRole("ADMIN")).thenReturn(1);
            Mockito.when(userDao.countEnabled()).thenReturn(5);

            assertEquals(Integer.valueOf(0), service.stats().get("disabled"));
        }
    }

    /* ==================================================================== */
    /*  existsUsername                                                      */
    /* ==================================================================== */

    @Nested
    @DisplayName("existsUsername · 查重")
    class ExistsUsername {

        @Test
        @DisplayName("入参为 null 时直接返回 false，不查库")
        void nullIsFalse() {
            assertFalse(service.existsUsername(null));
            Mockito.verifyNoInteractions(userDao);
        }

        @Test
        @DisplayName("入参裁剪空白后再查库")
        void trims() {
            Mockito.when(userDao.existsUsername("alice")).thenReturn(true);
            assertTrue(service.existsUsername("  alice  "));
        }

        @Test
        @DisplayName("纯空白入参裁剪为空串后照常查库")
        void blankBecomesEmpty() {
            Mockito.when(userDao.existsUsername("")).thenReturn(false);
            assertFalse(service.existsUsername("   "));
            Mockito.verify(userDao).existsUsername("");
        }

        @Test
        @DisplayName("用 ArgumentCaptor 校验传给 DAO 的确是裁剪后的登录名")
        void capturesTrimmedName() {
            Mockito.when(userDao.existsUsername("bob")).thenReturn(false);
            service.existsUsername(" bob ");

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            Mockito.verify(userDao).existsUsername(captor.capture());
            assertEquals("bob", captor.getValue());
            assertNotNull(captor.getValue());
        }
    }

    /* ==================================================================== */
    /*  growthTrend                                                         */
    /* ==================================================================== */

    @Nested
    @DisplayName("growthTrend · 用户增长趋势（ECharts 数据源）")
    class GrowthTrend {

        @Test
        @DisplayName("直接委托给 userDao.countByCreatedDate，原样透传")
        void delegatesToDao() {
            Map<String, Object> d1 = new LinkedHashMap<String, Object>();
            d1.put("date", "2026-09-01");
            d1.put("count", Integer.valueOf(3));
            Map<String, Object> d2 = new LinkedHashMap<String, Object>();
            d2.put("date", "2026-09-02");
            d2.put("count", Integer.valueOf(5));
            java.util.List<Map<String, Object>> rows = Arrays.asList(d1, d2);

            Mockito.when(userDao.countByCreatedDate()).thenReturn(rows);

            assertEquals(rows, service.growthTrend());
            Mockito.verify(userDao).countByCreatedDate();
        }

        @Test
        @DisplayName("DAO 返回空列表时透传空列表，不抛异常")
        void emptyTrend() {
            Mockito.when(userDao.countByCreatedDate())
                    .thenReturn(Collections.<Map<String, Object>>emptyList());
            assertTrue(service.growthTrend().isEmpty());
        }
    }
}
