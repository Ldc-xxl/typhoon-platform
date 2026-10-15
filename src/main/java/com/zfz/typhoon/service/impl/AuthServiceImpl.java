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
import com.zfz.typhoon.util.DataAccessException;
import com.zfz.typhoon.util.DateUtil;
import com.zfz.typhoon.util.PasswordUtil;

import java.sql.SQLException;
import java.time.LocalDateTime;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

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

    /** 注册登录名规则：3—50 位，仅字母 / 数字 / 下划线 */
    private static final Pattern REGISTER_USERNAME_PATTERN =
            Pattern.compile("^[A-Za-z0-9_]{3,50}$");

    /** 注册邮箱规则（只做基本格式校验，不追求 RFC 完备，与 UserServiceImpl 同一口径） */
    private static final Pattern REGISTER_EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /** 昵称最大长度（对齐 users.nickname varchar(50)） */
    private static final int NICKNAME_MAX = 50;

    /** 邮箱最大长度（对齐 users.email varchar(100)） */
    private static final int EMAIL_MAX = 100;

    /**
     * 注册时「用户名已被占用」的业务码。
     *
     * <p>取 HTTP 语义的 <b>409 Conflict</b>：与参数不合法（400）区分开，
     * 前端可以据此把提示定位到"用户名"这一项，而不是笼统地当成参数错误。
     *
     * <p>项目既有的 {@code Result} 已定义 200/400/401/403/404/500，唯独没有冲突码；
     * {@code Result} 不在本次改动范围内，故在此就近定义为公开常量，
     * 便于测试与前端契约互相引用，也避免把裸 {@code 409} 散落在代码里。
     */
    public static final int CODE_CONFLICT = 409;

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
        // 渐进迁移：老账号的口令摘要是早期格式（全局盐 / 不加盐 / 裸 hex），
        // 在"用户刚刚用正确口令证明了自己"这个唯一时机把它换成随机盐格式。
        // 刻意不在 verify() 里就地重写——校验是只读语义，写入放这里更干净。
        migrateHashIfLegacy(user, password);
        // 记录最后登录时间。放在这里而不是 toLoginVO 里：
        // 那是纯映射方法，不该有副作用。
        // 不复用 updated_at —— 改昵称、重置口令都会刷新它，语义对不上。
        userDao.updateLastLogin(user.getUserId());
        return toLoginVO(user);
    }

    /**
     * 把历史格式的口令摘要升级为当前的随机盐格式。
     *
     * <p>只在不影响登录的前提下尽力而为：升级失败（写库异常、账号被并发删除）
     * 只吞掉——用户已经证明自己持有正确口令，没有理由因为一次"锦上添花"的
     * 格式迁移把他挡在门外。
     *
     * @param user        已通过口令校验的用户
     * @param rawPassword 用户本次提交的明文口令（用于按新格式重算）
     */
    private void migrateHashIfLegacy(User user, String rawPassword) {
        try {
            if (!PasswordUtil.needsUpgrade(user.getPasswordHash())) {
                return;
            }
            userDao.updatePassword(user.getUserId(), PasswordUtil.hash(rawPassword));
        } catch (Exception e) {
            // 迁移失败不影响本次登录
        }
    }

    @Override
    public LoginVO register(String username, String password, String nickname, String email) {
        String account = validateRegisterUsername(username);
        validateRegisterPassword(password);
        String nick = resolveNickname(nickname, account);
        String mail = validateRegisterEmail(email);

        // 前置查重：正常路径下能给出「用户名已被占用」这种可读提示。
        // 真正兜底的是 users.username 的唯一键——并发注册同名账号时，
        // 两个请求可能同时通过这一步检查，靠下面的 insert 冲突兜住（见 insertNewUser）。
        if (userDao.existsUsername(account)) {
            throw new BusinessException(CODE_CONFLICT, "用户名已被注册，请换一个");
        }

        User user = new User();
        user.setUsername(account);
        // 口令一律走 PasswordUtil.hash（随机盐格式），绝不存明文、也绝不自行拼摘要格式——
        // 摘要格式一旦分叉，登录侧的 verify 与 UserServiceImplTest 的写死断言会一起挂掉。
        user.setPasswordHash(PasswordUtil.hash(password));
        user.setNickname(nick);
        user.setEmail(mail);
        // 角色强制为普通用户。请求体里的 role 在 RegisterRequest 上没有对应字段，
        // Jackson 又配置为忽略未知字段，因此不存在"注册时就指定成 ADMIN"的入口。
        user.setRole(User.ROLE_USER);
        user.setStatus(Boolean.TRUE);
        user.setUserId(Long.valueOf(insertNewUser(user)));
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
     * <p><b>计数递增完全交给数据库</b>（{@link LoginAttemptDao#incrementFail}）：
     * 若在这里"先查 → 算 count+1 → 再写回"，并发错误口令请求会互相覆盖计数，
     * {@code fail_count} 永远停在 1，5 次锁定形同虚设。这里只负责在自增完成后
     * 读回结果、判断是否达到 {@value #MAX_FAIL}，达到则写入锁定截止时间。
     *
     * @param account 登录名
     */
    private void recordFail(String account) {
        if (attemptDao != null) {
            try {
                LocalDateTime now = DateUtil.now();
                // 原子自增（含"距上次失败过久则重新计数"的判断，一并在 SQL 内完成）
                attemptDao.incrementFail(account, now, FAIL_TTL_MINUTES);
                LoginAttempt a = attemptDao.findByUsername(account);
                if (a != null && a.getFailCount() != null
                        && a.getFailCount().intValue() >= MAX_FAIL) {
                    // 写入锁定截止时间，并把计数归零（setLocked 内完成），
                    // 避免解锁瞬间又被立刻锁上
                    attemptDao.setLocked(account, DateUtil.plusHours(now, LOCK_MINUTES / 60.0));
                }
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
     * 落库新用户，并把唯一键冲突翻译成 409。
     *
     * <p>为什么除了前置查重还要包这层：{@link UserDao#existsUsername} 与
     * {@link UserDao#insert} 之间存在竞态窗口，两个请求同时注册同一个名字时，
     * 两边都可能通过前置检查。数据库的唯一键会拒绝后一条插入，
     * 若不翻译，用户看到的是「服务器内部错误」（500），
     * 而真实原因只是他想要的名字刚好被抢注了——提示必须有指导意义。
     *
     * @param user 待插入的用户（口令摘要已就绪）
     * @return 新生成的主键
     */
    private long insertNewUser(User user) {
        try {
            return userDao.insert(user);
        } catch (DataAccessException e) {
            if (isDuplicateKey(e)) {
                throw new BusinessException(CODE_CONFLICT, "用户名已被注册，请换一个");
            }
            throw e;
        }
    }

    /**
     * 判断数据访问异常是否为「唯一键冲突」。
     *
     * <p>判据是底层 {@link SQLException#getSQLState()} 的类别码 <b>23</b>
     * （Integrity Constraint Violation）。按状态码而不是匹配错误文案，
     * 是因为文案随驱动版本与语言变化，用状态码才不会在某次升级后悄悄失效。
     *
     * @param e 数据访问异常
     * @return 唯一键冲突返回 true
     */
    private static boolean isDuplicateKey(DataAccessException e) {
        Throwable cause = e == null ? null : e.getCause();
        if (cause instanceof SQLException) {
            String state = ((SQLException) cause).getSQLState();
            return state != null && state.startsWith("23");
        }
        return false;
    }

    /**
     * 校验注册登录名。
     *
     * @param username 原始登录名
     * @return 去首尾空白后的登录名
     */
    private String validateRegisterUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw BusinessException.badRequest("用户名不能为空");
        }
        String v = username.trim();
        if (!REGISTER_USERNAME_PATTERN.matcher(v).matches()) {
            throw BusinessException.badRequest("用户名需为 3—50 位字母、数字或下划线");
        }
        return v;
    }

    /**
     * 校验注册口令长度。
     *
     * <p>长度上下界复用改密那套常量（{@link #MIN_PASSWORD_LENGTH} /
     * {@link #MAX_PASSWORD_LENGTH}），避免同一策略维护出两个数字。
     *
     * @param password 明文口令
     */
    private void validateRegisterPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw BusinessException.badRequest("密码长度不能少于 " + MIN_PASSWORD_LENGTH + " 位");
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw BusinessException.badRequest("密码长度不能超过 " + MAX_PASSWORD_LENGTH + " 位");
        }
    }

    /**
     * 规整昵称：为空时回退成登录名。
     *
     * <p>不能落成空串——顶栏与用户表都取昵称首字做头像，
     * 空昵称会渲染出一个空白头像，看起来像坏了。
     *
     * @param nickname 原始昵称
     * @param username 登录名
     * @return 最终昵称
     */
    private String resolveNickname(String nickname, String username) {
        if (nickname == null || nickname.trim().isEmpty()) {
            return username;
        }
        String v = nickname.trim();
        if (v.length() > NICKNAME_MAX) {
            throw BusinessException.badRequest("昵称不能超过 " + NICKNAME_MAX + " 个字符");
        }
        return v;
    }

    /**
     * 校验注册邮箱：要么为空，要么格式合法。
     *
     * <p>空值统一落成 {@code null} 而不是空串——空串会绕过格式校验，
     * 又在库里留下一个"看起来填过"的空洞。
     *
     * @param email 原始邮箱
     * @return 规范化后的邮箱；未填返回 null
     */
    private String validateRegisterEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }
        String v = email.trim();
        if (v.length() > EMAIL_MAX) {
            throw BusinessException.badRequest("邮箱不能超过 " + EMAIL_MAX + " 个字符");
        }
        if (!REGISTER_EMAIL_PATTERN.matcher(v).matches()) {
            throw BusinessException.badRequest("邮箱格式不正确");
        }
        return v;
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
