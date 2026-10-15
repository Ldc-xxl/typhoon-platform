package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.LoginAttemptDao;
import com.zfz.typhoon.dao.UserDao;
import com.zfz.typhoon.model.LoginAttempt;
import com.zfz.typhoon.model.User;
import com.zfz.typhoon.model.vo.LoginVO;
import com.zfz.typhoon.model.vo.UserVO;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.DateUtil;
import com.zfz.typhoon.util.PasswordUtil;
import com.zfz.typhoon.util.Result;
import org.junit.jupiter.api.AfterEach;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AuthServiceImpl} 单元测试。
 *
 * <p>被测对象是全站唯一的登录闸门，出错只有两种后果：把正确的人挡在外面，
 * 或把错误的人放进来。因此本类覆盖三组行为：
 * <ol>
 *   <li><b>口令校验</b>——同时覆盖本项目加盐摘要与课程初始化脚本的不加盐摘要，
 *       两种存量数据都必须能登录；</li>
 *   <li><b>失败锁定</b>——连续 {@code MAX_FAIL} 次失败后锁定，且锁定期内
 *       即使口令正确也拒绝；</li>
 *   <li><b>账号枚举防护</b>——"账号不存在"与"口令错误"必须返回同一条提示。</li>
 * </ol>
 *
 * <h3>测试隔离注意</h3>
 * 失败计数器 {@code FAILS} 是 <b>静态</b> 的，会跨测试方法残留。
 * 因此每个用到登录的测试所涉及的账号，都在 {@link #setUp()} 与
 * {@link #tearDown()} 里显式 {@code unlock()} 一遍。
 *
 * @author 追风者项目组
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl · 认证业务")
class AuthServiceImplTest {

    /** 明文口令（与被测代码里的摘要算法配套，用于构造存量数据） */
    private static final String RAW = "password";

    /** 全部会被登录测试触及的账号，用于清理静态失败计数 */
    private static final String[] ACCOUNTS = {
            "alice", "bob", "locked_user", "disabled_user", "unknown", "  alice  "
    };

    @Mock
    private UserDao userDao;

    @Mock
    private LoginAttemptDao attemptDao;

    private AuthServiceImpl service;

    /** 走 login_attempts 表的服务实例（与上面的纯内存版区分开） */
    private AuthServiceImpl dbService;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(userDao);
        dbService = new AuthServiceImpl(userDao, attemptDao);
        clearLockState();
    }

    @AfterEach
    void tearDown() {
        clearLockState();
    }

    private void clearLockState() {
        for (String a : ACCOUNTS) {
            service.unlock(a);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  测试数据构造                                                        */
    /* ------------------------------------------------------------------ */

    /** 构造一个普通用户，口令摘要为本项目的加盐格式。 */
    private User saltedUser(String username) {
        return user(username, PasswordUtil.hash(RAW), "USER", Boolean.TRUE);
    }

    /** 构造一个普通用户，口令摘要为课程初始化脚本的不加盐格式。 */
    private User plainUser(String username) {
        return user(username, PasswordUtil.ALGO_PREFIX + PasswordUtil.plainSha256Hex(RAW),
                "USER", Boolean.TRUE);
    }

    private User user(String username, String hash, String role, Boolean status) {
        User u = new User();
        u.setUserId(7L);
        u.setUsername(username);
        u.setPasswordHash(hash);
        u.setRole(role);
        u.setStatus(status);
        u.setNickname(username + " 的昵称");
        return u;
    }

    /** 断言抛出的业务异常码与提示片段。 */
    private static void assertBusinessError(int expectedCode, String expectedPart,
                                            Runnable action) {
        BusinessException e = assertThrows(BusinessException.class, action::run);
        assertEquals(expectedCode, e.getCode(), "业务码不符：" + e.getMessage());
        org.junit.jupiter.api.Assertions.assertTrue(
                e.getMessage() != null && e.getMessage().contains(expectedPart),
                "提示文案应包含「" + expectedPart + "」，实际：" + e.getMessage());
    }

    /* ==================================================================== */
    /*  登录                                                                */
    /* ==================================================================== */

    @Nested
    @DisplayName("login · 参数校验")
    class LoginValidation {

        @Test
        @DisplayName("用户名为 null / 空串 / 纯空白一律 400，且不查库")
        void rejectsBlankUsername() {
            assertBusinessError(400, "用户名不能为空", () -> service.login(null, RAW));
            assertBusinessError(400, "用户名不能为空", () -> service.login("", RAW));
            assertBusinessError(400, "用户名不能为空", () -> service.login("   ", RAW));
            Mockito.verifyNoInteractions(userDao);
        }

        @Test
        @DisplayName("口令为 null / 空串一律 400，且不查库")
        void rejectsBlankPassword() {
            assertBusinessError(400, "密码不能为空", () -> service.login("alice", null));
            assertBusinessError(400, "密码不能为空", () -> service.login("alice", ""));
            Mockito.verifyNoInteractions(userDao);
        }
    }

    @Nested
    @DisplayName("login · 口令校验")
    class LoginCredentials {

        @Test
        @DisplayName("本项目加盐摘要的账号可登录")
        void acceptsSaltedHash() {
            Mockito.when(userDao.findByUsernameForAuth("alice")).thenReturn(saltedUser("alice"));

            LoginVO vo = service.login("alice", RAW);

            assertNotNull(vo);
            assertEquals(Long.valueOf(7L), vo.getUserId());
            assertEquals("alice", vo.getAccount());
            assertEquals("USER", vo.getRole());
            assertEquals("普通用户", vo.getRoleText());
            assertNotNull(vo.getToken());
            assertEquals(32, vo.getToken().length(), "token 应为去横线的 UUID");
        }

        @Test
        @DisplayName("课程初始化脚本的不加盐摘要同样可登录（存量数据兼容）")
        void acceptsPlainSha256Hash() {
            Mockito.when(userDao.findByUsernameForAuth("bob")).thenReturn(plainUser("bob"));

            assertEquals("bob", service.login("bob", RAW).getAccount());
        }

        @Test
        @DisplayName("登录成功后会刷新 last_login")
        void refreshesLastLogin() {
            Mockito.when(userDao.findByUsernameForAuth("alice")).thenReturn(saltedUser("alice"));

            service.login("alice", RAW);

            Mockito.verify(userDao).updateLastLogin(7L);
        }

        @Test
        @DisplayName("历史格式（不加盐）登录成功时被顺手升级为随机盐格式")
        void migratesLegacyHashOnLogin() {
            Mockito.when(userDao.findByUsernameForAuth("bob")).thenReturn(plainUser("bob"));

            service.login("bob", RAW);

            ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
            Mockito.verify(userDao).updatePassword(Mockito.eq(7L), hashCaptor.capture());
            String upgraded = hashCaptor.getValue();
            assertTrue(PasswordUtil.verify(RAW, upgraded), "升级后的摘要必须仍能校验原口令");
            assertFalse(PasswordUtil.needsUpgrade(upgraded), "升级后不应再被判为待升级");
        }

        @Test
        @DisplayName("已是当前格式的摘要不会被反复重写（否则每次登录都多一次无谓写库）")
        void doesNotRewriteCurrentFormatHash() {
            Mockito.when(userDao.findByUsernameForAuth("alice")).thenReturn(saltedUser("alice"));

            service.login("alice", RAW);

            Mockito.verify(userDao, Mockito.never())
                    .updatePassword(Mockito.anyLong(), Mockito.anyString());
        }

        @Test
        @DisplayName("升级写库失败不影响本次登录（用户已证明持有正确口令）")
        void migrationFailureDoesNotBlockLogin() {
            User legacy = plainUser("bob");
            Mockito.when(userDao.findByUsernameForAuth("bob")).thenReturn(legacy);
            Mockito.doThrow(new RuntimeException("写库失败"))
                    .when(userDao).updatePassword(Mockito.anyLong(), Mockito.anyString());

            assertEquals("bob", service.login("bob", RAW).getAccount());
        }

        @Test
        @DisplayName("登录名首尾空白会被裁掉后再查库")
        void trimsUsername() {
            Mockito.when(userDao.findByUsernameForAuth("alice")).thenReturn(saltedUser("alice"));

            assertEquals("alice", service.login("  alice  ", RAW).getAccount());
            Mockito.verify(userDao).findByUsernameForAuth("alice");
        }

        @Test
        @DisplayName("昵称优先作为展示名；昵称为空时回退到登录名")
        void displayNameFallsBackToUsername() {
            User withNick = saltedUser("alice");
            withNick.setNickname("追风少年");
            Mockito.when(userDao.findByUsernameForAuth("alice")).thenReturn(withNick);
            LoginVO vo = service.login("alice", RAW);
            assertEquals("追风少年", vo.getUserName());
            assertEquals("追", vo.getAvatarText());

            Mockito.when(userDao.findByUsernameForAuth("bob")).thenReturn(bobWithoutNickname());
            LoginVO vo2 = service.login("bob", RAW);
            assertEquals("bob", vo2.getUserName());
            assertEquals("b", vo2.getAvatarText());
        }

        private User bobWithoutNickname() {
            User u = saltedUser("bob");
            u.setNickname("   ");
            return u;
        }

        @Test
        @DisplayName("管理员的角色文案为「平台总管理员」")
        void adminRoleText() {
            User admin = user("alice", PasswordUtil.hash(RAW), "ADMIN", Boolean.TRUE);
            Mockito.when(userDao.findByUsernameForAuth("alice")).thenReturn(admin);

            LoginVO vo = service.login("alice", RAW);

            assertEquals("ADMIN", vo.getRole());
            assertEquals("平台总管理员", vo.getRoleText());
        }

        @Test
        @DisplayName("会话有效期不再由本服务写死，交给 Web 层回填")
        void expiresInNotSetByService() {
            Mockito.when(userDao.findByUsernameForAuth("alice")).thenReturn(saltedUser("alice"));

            LoginVO vo = service.login("alice", RAW);

            assertNull(vo.getExpiresIn(),
                    "expiresIn 应由 AuthServlet 依据 HttpSession 实际超时回填");
        }
    }

    @Nested
    @DisplayName("login · 账号枚举防护与停用")
    class LoginAccountState {

        @Test
        @DisplayName("账号不存在与口令错误返回同一条提示，不给枚举有效账号的机会")
        void unknownAccountAndWrongPasswordLookIdentical() {
            Mockito.when(userDao.findByUsernameForAuth("unknown")).thenReturn(null);
            Mockito.when(userDao.findByUsernameForAuth("alice")).thenReturn(saltedUser("alice"));

            BusinessException unknown = assertThrows(BusinessException.class,
                    () -> service.login("unknown", RAW));
            BusinessException wrong = assertThrows(BusinessException.class,
                    () -> service.login("alice", "wrong-password"));

            assertEquals(unknown.getMessage(), wrong.getMessage());
            assertEquals(Result.CODE_BAD_REQUEST, unknown.getCode());
        }

        @Test
        @DisplayName("口令错误时不写 last_login")
        void wrongPasswordDoesNotTouchLastLogin() {
            Mockito.when(userDao.findByUsernameForAuth("alice")).thenReturn(saltedUser("alice"));

            assertThrows(BusinessException.class, () -> service.login("alice", "wrong"));

            Mockito.verify(userDao, Mockito.never()).updateLastLogin(Mockito.anyLong());
        }

        @Test
        @DisplayName("口令正确但账号被停用：403，且提示明确（此时不存在枚举风险）")
        void disabledAccountRejected() {
            User disabled = user("disabled_user", PasswordUtil.hash(RAW), "USER", Boolean.FALSE);
            Mockito.when(userDao.findByUsernameForAuth("disabled_user")).thenReturn(disabled);

            assertBusinessError(403, "账号已被停用",
                    () -> service.login("disabled_user", RAW));
        }

        @Test
        @DisplayName("status 为 null 视为启用（老数据未填该列时不误杀）")
        void nullStatusTreatedAsEnabled() {
            User u = user("alice", PasswordUtil.hash(RAW), "USER", null);
            Mockito.when(userDao.findByUsernameForAuth("alice")).thenReturn(u);

            assertNotNull(service.login("alice", RAW));
        }
    }

    @Nested
    @DisplayName("login · 失败锁定")
    class LoginLockout {

        @Test
        @DisplayName("连续 5 次失败后第 6 次直接拒绝，且不再查库")
        void locksAfterFiveFailures() {
            Mockito.when(userDao.findByUsernameForAuth("locked_user"))
                    .thenReturn(saltedUser("locked_user"));

            for (int i = 1; i <= 5; i++) {
                assertBusinessError(400, "用户名或密码错误",
                        () -> service.login("locked_user", "wrong"));
            }
            Mockito.clearInvocations(userDao);

            // 第 6 次：即便口令正确也被拒
            assertBusinessError(403, "账号已锁定", () -> service.login("locked_user", RAW));
            Mockito.verifyNoInteractions(userDao);
        }

        @Test
        @DisplayName("解锁后可以正常登录")
        void unlockRestoresLogin() {
            Mockito.when(userDao.findByUsernameForAuth("locked_user"))
                    .thenReturn(saltedUser("locked_user"));
            for (int i = 0; i < 5; i++) {
                assertThrows(BusinessException.class, () -> service.login("locked_user", "wrong"));
            }
            assertBusinessError(403, "账号已锁定", () -> service.login("locked_user", RAW));

            service.unlock("locked_user");

            assertNotNull(service.login("locked_user", RAW));
        }

        @Test
        @DisplayName("登录成功会清空失败计数（此前几次失败不再累计）")
        void successClearsFailCount() {
            Mockito.when(userDao.findByUsernameForAuth("alice")).thenReturn(saltedUser("alice"));
            for (int i = 0; i < 4; i++) {
                assertThrows(BusinessException.class, () -> service.login("alice", "wrong"));
            }

            assertNotNull(service.login("alice", RAW));

            // 计数已清零，再失败 4 次仍不应触发锁定
            for (int i = 0; i < 4; i++) {
                assertBusinessError(400, "用户名或密码错误",
                        () -> service.login("alice", "wrong"));
            }
        }

        @Test
        @DisplayName("unlock(null) 与 unlock 未知账号都不抛异常")
        void unlockIsNullSafe() {
            service.unlock(null);
            service.unlock("从未出现过的账号");
        }

        @Test
        @DisplayName("成功登录后不应再残留失败计数（换账号各自独立）")
        void failCounterIsPerAccount() {
            Mockito.when(userDao.findByUsernameForAuth("alice")).thenReturn(saltedUser("alice"));
            Mockito.when(userDao.findByUsernameForAuth("bob")).thenReturn(saltedUser("bob"));

            for (int i = 0; i < 4; i++) {
                assertThrows(BusinessException.class, () -> service.login("alice", "wrong"));
            }

            // bob 从未失败过，应当一次成功
            assertNotNull(service.login("bob", RAW));
            // alice 仍可再失败一次（还没到 5 次）
            assertBusinessError(400, "用户名或密码错误", () -> service.login("alice", "wrong"));
        }
    }

    /* ==================================================================== */
    /*  其它方法                                                            */
    /* ==================================================================== */

    @Nested
    @DisplayName("profile · 当前用户资料")
    class Profile {

        @Test
        @DisplayName("用户不存在抛 404")
        void notFound() {
            Mockito.when(userDao.findById(99L)).thenReturn(null);
            assertBusinessError(404, "用户不存在", () -> service.profile(99L));
        }

        @Test
        @DisplayName("lastLogin 取 users.last_login，而不再拿 updated_at 冒充")
        void usesRealLastLogin() {
            User u = saltedUser("alice");
            u.setCreatedAt(LocalDateTime.of(2024, 2, 1, 10, 0, 0));
            u.setUpdatedAt(LocalDateTime.of(2026, 9, 1, 9, 0, 0));      // 改资料时间
            u.setLastLogin(LocalDateTime.of(2026, 9, 20, 21, 30, 0));   // 真正的登录时间
            Mockito.when(userDao.findById(7L)).thenReturn(u);

            UserVO vo = service.profile(7L);

            assertEquals("2024-02-01", vo.getRegistered());
            assertEquals("2026-09-20 21:30:00", vo.getLastLogin());
        }

        @Test
        @DisplayName("从未登录过的账号 lastLogin 为 null，而不是编一个时间")
        void neverLoggedInIsNull() {
            User u = saltedUser("alice");
            u.setUpdatedAt(LocalDateTime.of(2026, 9, 1, 9, 0, 0));
            u.setLastLogin(null);
            Mockito.when(userDao.findById(7L)).thenReturn(u);

            assertNull(service.profile(7L).getLastLogin());
        }

        @Test
        @DisplayName("资料中不携带口令摘要")
        void neverExposesPasswordHash() {
            Mockito.when(userDao.findById(7L)).thenReturn(saltedUser("alice"));
            UserVO vo = service.profile(7L);
            assertFalse(vo.toString().contains("sha256$"));
        }
    }

    @Nested
    @DisplayName("changePassword · 修改口令")
    class ChangePassword {

        @Test
        @DisplayName("用户不存在抛 404")
        void userNotFound() {
            Mockito.when(userDao.findById(99L)).thenReturn(null);
            assertBusinessError(404, "用户不存在",
                    () -> service.changePassword(99L, RAW, "newPass1"));
        }

        @Test
        @DisplayName("原口令不正确抛 400，且不写库")
        void wrongOldPassword() {
            Mockito.when(userDao.findById(7L)).thenReturn(saltedUser("alice"));

            assertBusinessError(400, "原密码不正确",
                    () -> service.changePassword(7L, "wrong", "newPass1"));
            Mockito.verify(userDao, Mockito.never())
                    .updatePassword(Mockito.anyLong(), Mockito.anyString());
        }

        @Test
        @DisplayName("原口令为 null 抛 400")
        void nullOldPassword() {
            Mockito.when(userDao.findById(7L)).thenReturn(saltedUser("alice"));
            assertBusinessError(400, "原密码不正确",
                    () -> service.changePassword(7L, null, "newPass1"));
        }

        @Test
        @DisplayName("新口令少于 6 位抛 400")
        void newPasswordTooShort() {
            Mockito.when(userDao.findById(7L)).thenReturn(saltedUser("alice"));
            assertBusinessError(400, "长度不能少于",
                    () -> service.changePassword(7L, RAW, "abc"));
        }

        @Test
        @DisplayName("新口令超过 64 位抛 400（长度校验先于字符重复校验）")
        void newPasswordTooLong() {
            Mockito.when(userDao.findById(7L)).thenReturn(saltedUser("alice"));

            // 全同字符的 65 位：若长度校验没排在前，会先报"单一字符重复"
            StringBuilder allSame = new StringBuilder();
            for (int i = 0; i < 65; i++) {
                allSame.append('a');
            }
            assertBusinessError(400, "长度不能超过",
                    () -> service.changePassword(7L, RAW, allSame.toString()));

            // 内容各异的 65 位
            StringBuilder varied = new StringBuilder();
            for (int i = 0; i < 65; i++) {
                varied.append((char) ('a' + (i % 26)));
            }
            assertBusinessError(400, "长度不能超过",
                    () -> service.changePassword(7L, RAW, varied.toString()));
        }

        @Test
        @DisplayName("新口令为 null 视为过短")
        void nullNewPassword() {
            Mockito.when(userDao.findById(7L)).thenReturn(saltedUser("alice"));
            assertBusinessError(400, "长度不能少于",
                    () -> service.changePassword(7L, RAW, null));
        }

        @Test
        @DisplayName("新口令是单一字符的重复时抛 400")
        void allSameCharacterRejected() {
            Mockito.when(userDao.findById(7L)).thenReturn(saltedUser("alice"));
            assertBusinessError(400, "单一字符",
                    () -> service.changePassword(7L, RAW, "aaaaaaaa"));
        }

        @Test
        @DisplayName("新旧口令相同时抛 400")
        void rejectsUnchangedPassword() {
            Mockito.when(userDao.findById(7L)).thenReturn(saltedUser("alice"));
            assertBusinessError(400, "不能与原密码相同",
                    () -> service.changePassword(7L, RAW, RAW));
        }

        @Test
        @DisplayName("合法修改：写入随机盐摘要，且不是明文")
        void writesHashedPassword() {
            Mockito.when(userDao.findById(7L)).thenReturn(saltedUser("alice"));

            service.changePassword(7L, RAW, "newPass1");

            // 不能用 PasswordUtil.hash("newPass1") 去比对：随机盐意味着两次调用
            // 必然得到不同的串（这正是本次改动的目的），只能捕获实参再复算校验。
            ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
            Mockito.verify(userDao).updatePassword(Mockito.eq(7L), hashCaptor.capture());
            String stored = hashCaptor.getValue();
            assertTrue(PasswordUtil.verify("newPass1", stored), "落库摘要必须能被新口令校验通过");
            assertFalse(PasswordUtil.needsUpgrade(stored), "应落当前的随机盐格式");
            assertFalse(stored.contains("newPass1"), "落库的绝不能是明文");
        }

        @Test
        @DisplayName("刚好 6 位的新口令可以通过（下边界）")
        void minimumLengthAccepted() {
            Mockito.when(userDao.findById(7L)).thenReturn(saltedUser("alice"));

            service.changePassword(7L, RAW, "abc123");

            ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
            Mockito.verify(userDao).updatePassword(Mockito.eq(7L), hashCaptor.capture());
            assertTrue(PasswordUtil.verify("abc123", hashCaptor.getValue()));
        }
    }

    @Nested
    @DisplayName("findByUsername · 便捷查询")
    class FindByUsername {

        @Test
        @DisplayName("入参为 null 时返回 null 且不查库")
        void nullReturnsNull() {
            assertNull(service.findByUsername(null));
            Mockito.verifyNoInteractions(userDao);
        }

        @Test
        @DisplayName("入参会先裁剪空白再查库")
        void trims() {
            Mockito.when(userDao.findByUsername("alice")).thenReturn(saltedUser("alice"));
            assertEquals("alice", service.findByUsername("  alice  ").getUsername());
        }

        @Test
        @DisplayName("查不到时如实返回 null")
        void notFound() {
            Mockito.when(userDao.findByUsername("ghost")).thenReturn(null);
            assertNull(service.findByUsername("ghost"));
        }
    }

    /* ==================================================================== */
    /*  失败锁定 —— 数据库持久化路径（login_attempts 表）                     */
    /* ==================================================================== */

    @Nested
    @DisplayName("login · 失败锁定（数据库持久化路径）")
    class DbLockout {

        /** 构造一条 login_attempts 记录（失败时间取"刚刚"）。 */
        private LoginAttempt attempt(int failCount, LocalDateTime lockedUntil) {
            LoginAttempt a = new LoginAttempt();
            a.setUsername("alice");
            a.setFailCount(Integer.valueOf(failCount));
            a.setLastFailAt(DateUtil.now());
            a.setLockedUntil(lockedUntil);
            return a;
        }

        @Test
        @DisplayName("库里记录处于锁定中 → 403，且不查用户表")
        void lockedInDbRejects() {
            Mockito.when(attemptDao.findByUsername("alice"))
                    .thenReturn(attempt(0, DateUtil.plusHours(DateUtil.now(), 0.25)));

            assertBusinessError(403, "账号已锁定", () -> dbService.login("alice", RAW));
            Mockito.verifyNoInteractions(userDao);
        }

        @Test
        @DisplayName("锁定已过期 → 正常放行（不因历史旧记录误杀）")
        void expiredLockAllows() {
            Mockito.when(attemptDao.findByUsername("alice"))
                    .thenReturn(attempt(0, DateUtil.plusHours(DateUtil.now(), -1)));
            Mockito.when(userDao.findByUsernameForAuth("alice")).thenReturn(saltedUser("alice"));

            assertNotNull(dbService.login("alice", RAW));
        }

        @Test
        @DisplayName("失败时写库：无历史记录则从 1 开始计数（递增由 SQL 原子完成）")
        void firstFailureWritesCountOne() {
            Mockito.when(attemptDao.findByUsername("alice")).thenReturn(null);
            Mockito.when(userDao.findByUsernameForAuth("alice")).thenReturn(saltedUser("alice"));

            assertThrows(BusinessException.class, () -> dbService.login("alice", "wrong"));

            // 次数不再由 Java 侧计算后写回，而是交给 SQL 原子自增；这里只断言
            // 递增被调用、且 TTL 传的是配置的连续失败窗口（30 分钟）
            Mockito.verify(attemptDao).incrementFail(Mockito.eq("alice"),
                    Mockito.any(LocalDateTime.class), Mockito.eq(30));
            // 未达阈值，不应写锁定
            Mockito.verify(attemptDao, Mockito.never())
                    .setLocked(Mockito.anyString(), Mockito.any(LocalDateTime.class));
        }

        @Test
        @DisplayName("达到阈值：写入锁定截止时间（计数归零由 SQL 一并完成）")
        void thresholdLocksAndResetsCount() {
            // 第一次查询（checkLocked）时计数为 4 且未锁定 → 放行；
            // SQL 自增后再读回已是 5 → 触发锁定
            Mockito.when(attemptDao.findByUsername("alice"))
                    .thenReturn(attempt(4, null), attempt(5, null));
            Mockito.when(userDao.findByUsernameForAuth("alice")).thenReturn(saltedUser("alice"));

            assertThrows(BusinessException.class, () -> dbService.login("alice", "wrong"));

            Mockito.verify(attemptDao).setLocked(Mockito.eq("alice"),
                    Mockito.any(LocalDateTime.class));
        }

        @Test
        @DisplayName("登录成功 → 清除库里的失败记录")
        void successClearsRecord() {
            Mockito.when(attemptDao.findByUsername("alice")).thenReturn(null);
            Mockito.when(userDao.findByUsernameForAuth("alice")).thenReturn(saltedUser("alice"));

            assertNotNull(dbService.login("alice", RAW));

            Mockito.verify(attemptDao).delete("alice");
        }

        @Test
        @DisplayName("★ 表缺失 / 库异常时降级内存：登录仍能成功，不因辅助表挂掉而登不进去")
        void dbFailureDegradesToMemory() {
            Mockito.when(attemptDao.findByUsername("alice"))
                    .thenThrow(new RuntimeException("Table 'login_attempts' doesn't exist"));
            Mockito.when(userDao.findByUsernameForAuth("alice")).thenReturn(saltedUser("alice"));

            assertNotNull(dbService.login("alice", RAW));
        }

        @Test
        @DisplayName("★ 写库异常时失败仍被记录：抛业务异常而非 RuntimeException")
        void dbWriteFailureStillRecordsFail() {
            Mockito.when(attemptDao.findByUsername("alice"))
                    .thenThrow(new RuntimeException("connection refused"));
            Mockito.when(userDao.findByUsernameForAuth("alice")).thenReturn(saltedUser("alice"));

            // 期望抛的是「口令错误」业务异常，而不是底层 RuntimeException
            assertBusinessError(400, "用户名或密码错误", () -> dbService.login("alice", "wrong"));
        }
    }
}
