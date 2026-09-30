package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.UserDao;
import com.zfz.typhoon.dao.impl.UserDaoImpl;
import com.zfz.typhoon.model.User;
import com.zfz.typhoon.model.vo.UserVO;
import com.zfz.typhoon.service.UserService;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.PasswordUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 用户管理业务实现。
 *
 * <h3>关于 {@code lastLogin}</h3>
 * {@code users} 表已新增 {@code last_login datetime NULL} 列，
 * 由 {@code AuthServiceImpl.login()} 在登录成功后写入（时间取数据库 {@code NOW()}）。
 * 此前用 {@code updated_at} 兜底属于语义妥协 —— 改昵称、重置口令都会刷新它，
 * 后台会显示成"刚登录过" —— 现已改为读取真正的登录时间；
 * 从未登录过的账号该字段为 NULL，前端显示"—"。
 *
 * @author 追风者项目组
 * @see UserService
 */
public class UserServiceImpl implements UserService {

    /** 登录名规则：字母开头，允许字母数字下划线，4—50 位 */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{3,49}$");

    /** 邮箱规则（只做基本格式校验，不追求 RFC 完备） */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /** 中国大陆手机号 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /** 昵称最大长度（对齐 users.nickname varchar(50)） */
    private static final int NICKNAME_MAX = 50;

    private final UserDao userDao;

    /**
     * 生产用构造：按默认实现装配依赖。
     * <p>之所以把它拆成"无参 + 带参"两个构造，是为了给单元测试留出注入替身的缝隙：
     * 依赖全部在字段初始化里 new 出来的话，测试就必须连上真实 MySQL 才能跑，
     * 单元测试也就退化成了集成测试。
     */
    public UserServiceImpl() {
        this(new UserDaoImpl());
    }

    /**
     * 测试用构造：注入替身依赖（Mockito 生成的 mock 或手写桩）。
     * <p>参数顺序与上方字段声明一致，均为本服务运行所需的协作者。
     */
    public UserServiceImpl(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public List<UserVO> list(String keyword, String role, Boolean enabled) {
        String kw = keyword == null || keyword.trim().isEmpty() ? null : keyword.trim();
        String r = role == null || role.trim().isEmpty() ? null : role.trim().toUpperCase();
        if (r != null && !User.ROLE_ADMIN.equals(r) && !User.ROLE_USER.equals(r)) {
            throw BusinessException.badRequest("role 取值非法，应为 ADMIN 或 USER");
        }
        List<User> rows = userDao.search(kw, r, enabled);
        List<UserVO> result = new ArrayList<UserVO>(rows.size());
        for (User u : rows) {
            result.add(UserVO.of(u, null));
        }
        return result;
    }

    @Override
    public UserVO getById(long userId) {
        User user = userDao.findById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在：" + userId);
        }
        return UserVO.of(user, null);
    }

    @Override
    public long create(User user, String rawPassword) {
        if (user == null) {
            throw BusinessException.badRequest("用户信息不能为空");
        }
        String username = validateUsername(user.getUsername());
        user.setUsername(username);
        if (userDao.existsUsername(username)) {
            throw BusinessException.badRequest("登录名已被占用：" + username);
        }
        validateProfile(user);
        validatePassword(rawPassword);

        String role = user.getRole() == null || user.getRole().trim().isEmpty()
                ? User.ROLE_USER : user.getRole().trim().toUpperCase();
        if (!User.ROLE_ADMIN.equals(role) && !User.ROLE_USER.equals(role)) {
            throw BusinessException.badRequest("role 取值非法，应为 ADMIN 或 USER");
        }
        user.setRole(role);
        user.setPasswordHash(PasswordUtil.hash(rawPassword));
        if (user.getStatus() == null) {
            user.setStatus(Boolean.TRUE);
        }
        if (user.getNickname() == null || user.getNickname().trim().isEmpty()) {
            user.setNickname(username);
        }
        return userDao.insert(user);
    }

    @Override
    public int updateProfile(User user) {
        if (user == null || user.getUserId() == null) {
            throw BusinessException.badRequest("缺少 userId");
        }
        requireUser(user.getUserId().longValue());
        // 登录名不允许改：它是审计日志的锚点，改了会让历史记录失去指向
        user.setUsername(null);
        user.setPasswordHash(null);
        validateProfile(user);
        return userDao.updateProfile(user);
    }

    @Override
    public int setEnabled(long userId, boolean enabled, long operatorId) {
        User target = requireUser(userId);
        if (!enabled) {
            if (userId == operatorId) {
                throw BusinessException.forbidden("不能停用当前登录账号");
            }
            guardLastAdmin(target, "停用");
        }
        int rows = userDao.setEnabled(userId, enabled);
        if (rows == 0) {
            throw BusinessException.notFound("用户不存在：" + userId);
        }
        return rows;
    }

    @Override
    public int setRole(long userId, String role, long operatorId) {
        if (role == null) {
            throw BusinessException.badRequest("缺少 role");
        }
        String target = role.trim().toUpperCase();
        if (!User.ROLE_ADMIN.equals(target) && !User.ROLE_USER.equals(target)) {
            throw BusinessException.badRequest("role 取值非法，应为 ADMIN 或 USER");
        }
        User user = requireUser(userId);
        if (User.ROLE_USER.equals(target)) {
            if (userId == operatorId) {
                throw BusinessException.forbidden("不能降低当前登录账号的权限");
            }
            guardLastAdmin(user, "降级");
        }
        int rows = userDao.setRole(userId, target);
        if (rows == 0) {
            throw BusinessException.notFound("用户不存在：" + userId);
        }
        return rows;
    }

    @Override
    public int resetPassword(long userId, String newPassword) {
        requireUser(userId);
        validatePassword(newPassword);
        int rows = userDao.updatePassword(userId, PasswordUtil.hash(newPassword));
        if (rows == 0) {
            throw BusinessException.notFound("用户不存在：" + userId);
        }
        return rows;
    }

    @Override
    public int delete(long userId, long operatorId) {
        if (userId == operatorId) {
            throw BusinessException.forbidden("不能删除当前登录账号");
        }
        User user = requireUser(userId);
        guardLastAdmin(user, "删除");
        int rows = userDao.deleteById(userId);
        if (rows == 0) {
            throw BusinessException.notFound("用户不存在：" + userId);
        }
        return rows;
    }

    @Override
    public Map<String, Integer> stats() {
        int total = userDao.countAll();
        int admin = userDao.countByRole(User.ROLE_ADMIN);
        int normal = userDao.countEnabled();
        Map<String, Integer> m = new LinkedHashMap<String, Integer>();
        m.put("total", Integer.valueOf(total));
        m.put("admin", Integer.valueOf(admin));
        m.put("normal", Integer.valueOf(normal));
        m.put("disabled", Integer.valueOf(Math.max(0, total - normal)));
        return m;
    }

    @Override
    public boolean existsUsername(String username) {
        return username != null && userDao.existsUsername(username.trim());
    }

    @Override
    public List<Map<String, Object>> growthTrend() {
        return userDao.countByCreatedDate();
    }

    /* ==================================================================== */
    /*  内部校验                                                            */
    /* ==================================================================== */

    /**
     * 取用户，不存在抛 404。
     *
     * @param userId 用户ID
     * @return 用户实体
     */
    private User requireUser(long userId) {
        User user = userDao.findById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在：" + userId);
        }
        return user;
    }

    /**
     * 拦截"动到最后一个管理员"的操作。
     *
     * <p>判定条件：目标当前是启用状态的管理员，且系统中启用状态的管理员只有他一个。
     * 必须同时判断"启用"——已停用的管理员本来就不具备管理能力，不构成最后一根稻草。
     *
     * @param target 目标用户
     * @param action 动作名（用于提示文案）
     */
    private void guardLastAdmin(User target, String action) {
        if (!target.isAdmin()) {
            return;
        }
        boolean targetActive = !Boolean.FALSE.equals(target.getStatus());
        if (!targetActive) {
            return;
        }
        int activeAdmins = 0;
        for (User u : userDao.listByRole(User.ROLE_ADMIN)) {
            if (!Boolean.FALSE.equals(u.getStatus())) {
                activeAdmins++;
            }
        }
        if (activeAdmins <= 1) {
            throw BusinessException.forbidden("系统必须保留至少一名启用状态的管理员，无法" + action);
        }
    }

    /**
     * 校验并规整登录名。
     *
     * @param username 原始登录名
     * @return 规整后的登录名
     */
    private String validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw BusinessException.badRequest("登录名不能为空");
        }
        String v = username.trim();
        if (!USERNAME_PATTERN.matcher(v).matches()) {
            throw BusinessException.badRequest("登录名需以字母开头，仅含字母/数字/下划线，长度 4—50");
        }
        return v;
    }

    /**
     * 校验邮箱、手机号、昵称等资料字段。
     *
     * <p>邮箱与手机号都必须"要么为空，要么合法"——
     * 空白字符串会绕过格式校验却在 UI 上留下一个看不出问题的空洞。
     *
     * @param user 用户实体
     */
    private void validateProfile(User user) {
        if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
            String email = user.getEmail().trim();
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                throw BusinessException.badRequest("邮箱格式不正确");
            }
            user.setEmail(email);
        } else {
            user.setEmail(null);
        }
        if (user.getPhone() != null && !user.getPhone().trim().isEmpty()) {
            String phone = user.getPhone().trim();
            if (!PHONE_PATTERN.matcher(phone).matches()) {
                throw BusinessException.badRequest("手机号格式不正确");
            }
            user.setPhone(phone);
        } else {
            user.setPhone(null);
        }
        if (user.getNickname() != null && user.getNickname().trim().length() > NICKNAME_MAX) {
            throw BusinessException.badRequest("昵称不能超过 " + NICKNAME_MAX + " 个字符");
        }
    }

    /**
     * 校验口令强度（与 {@link AuthServiceImpl} 保持同一套规则）。
     *
     * @param rawPassword 明文口令
     */
    private void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < AuthServiceImpl.MIN_PASSWORD_LENGTH) {
            throw BusinessException.badRequest("密码长度不能少于 " + AuthServiceImpl.MIN_PASSWORD_LENGTH + " 位");
        }
        if (rawPassword.length() > AuthServiceImpl.MAX_PASSWORD_LENGTH) {
            throw BusinessException.badRequest("密码长度不能超过 " + AuthServiceImpl.MAX_PASSWORD_LENGTH + " 位");
        }
    }
}
