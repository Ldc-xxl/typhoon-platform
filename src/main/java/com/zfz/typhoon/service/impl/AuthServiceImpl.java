package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.LoginAttemptDao;
import com.zfz.typhoon.dao.UserDao;
import com.zfz.typhoon.dao.impl.LoginAttemptDaoImpl;
import com.zfz.typhoon.dao.impl.UserDaoImpl;
import com.zfz.typhoon.model.LoginAttempt;
import com.zfz.typhoon.model.User;
import com.zfz.typhoon.model.vo.LoginVO;
import com.zfz.typhoon.model.vo.UserVO;
import com.zfz.typhoon.service.AuthService;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.DateUtil;
import com.zfz.typhoon.util.PasswordUtil;

import java.time.LocalDateTime;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证业务实现。
 *
 * <h3>登录失败锁定（已持久化）</h3>
 * 口令爆破是登录接口最现实的威胁。同一账号连续失败 {@value #MAX_FAIL} 次
 * 即锁定 {@value #LOCK_MINUTES} 分钟，期间即使口令正确也拒绝登录。
 *
 * <p><b>计数落在 {@code login_attempts} 表</b>（经 {@link LoginAttemptDao}），
 * 因此 Tomcat 重启后锁定依然有效——早先的进程内 Map 实现一重启就清零，
 * 攻击者只要挑重启窗口就能继续爆破。
 *
 * <h3>降级策略（重要）</h3>
 * 锁定只是<b>辅助防护</b>，绝不能因为它导致正常用户登不进去。因此：
 * <ul>
 *   <li>{@code login_attempts} 表缺失或数据库异常时，自动退回进程内计数
 *       （{@link #FAILS}），登录功能不受影响，只是重启后清零；</li>
 *   <li>清理失败记录（登录成功 / 管理员解锁）时出错也只 swallow，不影响已成功的登录。</li>
 * </ul>
 * 已存在的库需要执行 {@code db/migration_login_attempts.sql} 才会建这张表；
 * 没执行也能正常登录，只是回到旧行为。
 *
 * <p>内存计数使用 {@link ConcurrentHashMap}：Tomcat 的请求线程池会并发调用本类，
 * 用普通 HashMap 在并发 put 时可能出现环形链表导致 CPU 打满。
 *
 * @author 追风者项目组
 * @see AuthService
 */
public class AuthServiceImpl implements AuthService {

    /** 连续失败多少次后锁定 */
    private static final int MAX_FAIL = 5;

    /** 锁定时长（分钟） */
    private static final int LOCK_MINUTES = 15;

    /** 失败计数过期时间（分钟）：超过这个时间没再失败就清零 */
    private static final int FAIL_TTL_MINUTES = 30;

    /** 密码最短长度 */
    public static final int MIN_PASSWORD_LENGTH = 6;

    /** 密码最长长度（与 {@code users.password_hash} varchar(255) 留足余量） */
    public static final int MAX_PASSWORD_LENGTH = 64;

    /** 账号 → 失败状态（仅作数据库不可用时的降级兜底） */
    private static final Map<String, FailState> FAILS = new ConcurrentHashMap<String, FailState>();

    private final UserDao userDao;

    /** 登录失败锁定状态 DAO；为 {@code null} 时只走进程内计数 */
    private final LoginAttemptDao attemptDao;

    /**
     * 生产用构造：按默认实现装配依赖。
     * <p>之所以把它拆成"无参 + 带参"两个构造，是为了给单元测试留出注入替身的缝隙：
     * 依赖全部在字段初始化里 new 出来的话，测试就必须连上真实 MySQL 才能跑，
     * 单元测试也就退化成了集成测试。
     */
    public AuthServiceImpl() {
        this(new UserDaoImpl(), new LoginAttemptDaoImpl());
    }

    /**
     * 只注入用户 DAO 的构造：不接锁定表，退化为纯进程内计数。
     * <p>保留它是为了不破坏既有的单元测试（那些用例断言的是内存计数行为），
     * 也让"没有锁定表"这一降级路径本身可被测试覆盖。
     *
     * @param userDao 用户 DAO
     */
    public AuthServiceImpl(UserDao userDao) {
        this(userDao, null);
    }

    /**
     * 测试用构造：注入替身依赖（Mockito 生成的 mock 或手写桩）。
     * <p>参数顺序与上方字段声明一致，均为本服务运行所需的协作者。
     *
     * @param userDao    用户 DAO
     * @param attemptDao 登录失败锁定 DAO，传 {@code null} 表示只走内存
     */
    public AuthServiceImpl(UserDao userDao, LoginAttemptDao attemptDao) {
        this.userDao = userDao;
        this.attemptDao = attemptDao;
    }

    /** 单个账号的失败状态 */
    private static final class FailState {
        /** 连续失败次数 */
        private int count;
        /** 最近一次失败时间（毫秒） */
        private long lastFailAt;
        /** 锁定截止时间（毫秒），0 表示未锁定 */
        private long lockedUntil;
    }

    @Override
    public LoginVO login(String username, String password) {
        String account = username == null ? "" : username.trim();
        if (account.isEmpty()) {
            throw BusinessException.badRequest("用户名不能为空");
        }
        if (password == null || password.isEmpty()) {
            throw BusinessException.badRequest("密码不能为空");
        }

        // 先查锁定状态：锁定期间不查用户表、不比对口令，直接拒绝
        checkLocked(account);

        User user = userDao.findByUsernameForAuth(account);
        // 账号不存在与口令错误返回同一条提示：不给攻击者枚举有效账号的机会
        if (user == null || !PasswordUtil.verify(password, user.getPasswordHash())) {
            recordFail(account);
            throw BusinessException.badRequest("用户名或密码错误");
        }

        // 口令正确但账号被停用：这里可以说得更明确，
        // 因为能走到这一步说明对方已持有正确口令，不存在账号枚举风险
        if (Boolean.FALSE.equals(user.getStatus())) {
            throw BusinessException.forbidden("账号已被停用，请联系管理员");
        }

        clearFail(account);
        // 记录最后登录时间。放在这里而不是 toLoginVO 里：
        // 那是纯映射方法，不该有副作用。
        // 不复用 updated_at —— 改昵称、重置口令都会刷新它，语义对不上。
        userDao.updateLastLogin(user.getUserId());
        return toLoginVO(user);
    }

    @Override
    public UserVO profile(long userId) {
        User user = userDao.findById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        // lastLogin 传 null，由 UserVO.of 回退到 user.getLastLogin()（users.last_login 列）
        return UserVO.of(user, null);
    }

    @Override
    public void changePassword(long userId, String oldPassword, String newPassword) {
        User user = userDao.findById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        String stored = user.getPasswordHash();
        if (oldPassword == null || !PasswordUtil.verify(oldPassword, stored)) {
            throw BusinessException.badRequest("原密码不正确");
        }
        validateNewPassword(newPassword);
        if (PasswordUtil.verify(newPassword, stored)) {
            throw BusinessException.badRequest("新密码不能与原密码相同");
        }
        userDao.updatePassword(userId, PasswordUtil.hash(newPassword));
    }

    @Override
    public User findByUsername(String username) {
        return username == null ? null : userDao.findByUsername(username.trim());
    }

    @Override
    public void unlock(String username) {
        if (username != null) {
            clearFail(username.trim());
        }
    }

    /* ==================================================================== */
    /*  内部                                                                */
    /* ==================================================================== */

    /**
     * 账号处于锁定中时直接抛 403（锁定期间不查用户表、不比对口令）。
     *
     * <p>优先读 {@code login_attempts} 表；数据库不可用（表缺失 / 连接异常）时
     * 降级为进程内计数，保证登录功能不受影响。
     *
     * @param account 登录名
     * @throws BusinessException 403「账号已锁定，请 N 分钟后重试」
     */
    private void checkLocked(String account) {
        if (attemptDao != null) {
            try {
                LoginAttempt a = attemptDao.findByUsername(account);
                if (a != null && a.getLockedUntil() != null) {
                    double hours = DateUtil.hoursBetween(DateUtil.now(), a.getLockedUntil());
                    if (hours > 0) {
                        long minutes = Math.max(1L, (long) Math.ceil(hours * 60));
                        throw BusinessException.forbidden("账号已锁定，请 " + minutes + " 分钟后重试");
                    }
                }
                return;                 // 数据库路径已判定完毕，不必再看内存
            } catch (BusinessException e) {
                throw e;                // 锁定提示照原样抛出，不能被降级逻辑吞掉
            } catch (Exception e) {
                // 表缺失或连接异常 → 落到下面的内存分支
            }
        }
        FailState s = FAILS.get(account);
        long now = System.currentTimeMillis();
        if (s != null && s.lockedUntil > now) {
            long minutes = (s.lockedUntil - now) / 60000L + 1L;
            throw BusinessException.forbidden("账号已锁定，请 " + minutes + " 分钟后重试");
        }
    }

    /**
     * 记录一次失败，达到阈值即锁定账号。
     *
     * <p>优先写 {@code login_attempts} 表；写库失败时降级为进程内计数。
     *
     * @param account 登录名
     */
    private void recordFail(String account) {
        if (attemptDao != null) {
            try {
                LocalDateTime now = DateUtil.now();
                LoginAttempt a = attemptDao.findByUsername(account);
                int count = 1;
                if (a != null && a.getLastFailAt() != null) {
                    // 距上次失败太久说明不是连续爆破，重新计数
                    double gap = DateUtil.hoursBetween(a.getLastFailAt(), now);
                    count = gap > FAIL_TTL_MINUTES / 60.0 ? 1 : a.getFailCount() + 1;
                }
                LocalDateTime lockedUntil = null;
                if (count >= MAX_FAIL) {
                    lockedUntil = DateUtil.plusHours(now, LOCK_MINUTES / 60.0);
                    count = 0;          // 触发锁定后归零，避免解锁瞬间又被立刻锁上
                }
                attemptDao.save(account, count, now, lockedUntil);
                return;
            } catch (Exception e) {
                // 写库失败 → 落到下面的内存分支
            }
        }
        recordFailInMemory(account, System.currentTimeMillis());
    }

    /**
     * 清除失败记录（登录成功或管理员解锁时调用）。
     *
     * <p>内存与数据库两处都清。数据库清理失败只 swallow：
     * 此时用户已经证明自己知道口令，不该因为一次清理失败就拒绝本次登录。
     *
     * @param account 登录名
     */
    private void clearFail(String account) {
        FAILS.remove(account);
        if (attemptDao != null) {
            try {
                attemptDao.delete(account);
            } catch (Exception e) {
                // 清理失败不影响本次已成功的登录
            }
        }
    }

    /**
     * 进程内记录一次失败（数据库不可用时的降级路径）。
     *
     * @param account 登录名
     * @param now     当前毫秒时间戳
     */
    private void recordFailInMemory(String account, long now) {
        FailState state = FAILS.get(account);
        if (state == null) {
            state = new FailState();
            state.count = 1;
            state.lastFailAt = now;
            FAILS.put(account, state);
            return;
        }
        // 距上次失败太久说明不是连续爆破，重新计数
        if (now - state.lastFailAt > FAIL_TTL_MINUTES * 60000L) {
            state.count = 1;
        } else {
            state.count++;
        }
        state.lastFailAt = now;
        if (state.count >= MAX_FAIL) {
            state.lockedUntil = now + LOCK_MINUTES * 60000L;
            state.count = 0;
        }
    }

    /**
     * 由实体生成登录凭据。
     *
     * <p>token 只是一串写入浏览器 localStorage 的展示性标识，
     * <b>真正的会话状态存在 HttpSession 里</b>（由 Servlet 写入），
     * 因此这里不必承担签名与防篡改职责。
     *
     * <p><b>本方法刻意不设置 {@code expiresIn}</b>。会话有效期属于 Web 层概念，
     * 唯一来源是 {@code web.xml} 的 {@code <session-timeout>}；
     * 由 {@link com.zfz.typhoon.servlet.AuthServlet} 在建好会话后
     * 回读 {@code HttpSession.getMaxInactiveInterval()} 填回去。
     * 早先这里硬编码 1800 并在 Servlet 里 {@code setMaxInactiveInterval} 覆盖容器配置，
     * 同一个数值要维护两处、改一处就漂，已去掉。
     *
     * @param user 用户实体
     * @return 登录凭据
     */
    private LoginVO toLoginVO(User user) {
        LoginVO vo = new LoginVO();
        vo.setToken(UUID.randomUUID().toString().replace("-", ""));
        vo.setUserId(user.getUserId());
        vo.setAccount(user.getUsername());
        vo.setRole(user.getRole());
        vo.setRoleText(user.isAdmin() ? "平台总管理员" : "普通用户");
        String name = user.getNickname() == null || user.getNickname().trim().isEmpty()
                ? user.getUsername() : user.getNickname().trim();
        vo.setUserName(name);
        vo.setAvatarText(name.isEmpty() ? "?" : name.substring(0, 1));
        return vo;
    }

    /**
     * 新密码强度校验。
     *
     * <p>只做长度与"不能全是同一字符"这类零成本检查。
     * 不强制大小写数字混合：那类规则会真实地推高用户把口令写在便签上的概率，
     * 收益存疑；课程场景下长度约束已足够表达"口令策略"这一考点。
     *
     * @param newPassword 明文新密码
     */
    private void validateNewPassword(String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw BusinessException.badRequest("新密码长度不能少于 " + MIN_PASSWORD_LENGTH + " 位");
        }
        if (newPassword.length() > MAX_PASSWORD_LENGTH) {
            throw BusinessException.badRequest("新密码长度不能超过 " + MAX_PASSWORD_LENGTH + " 位");
        }
        boolean allSame = true;
        for (int i = 1; i < newPassword.length(); i++) {
            if (newPassword.charAt(i) != newPassword.charAt(0)) {
                allSame = false;
                break;
            }
        }
        if (allSame) {
            throw BusinessException.badRequest("新密码不能为单一字符的重复");
        }
    }
}
