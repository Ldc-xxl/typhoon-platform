package com.zfz.typhoon.dao;

import com.zfz.typhoon.model.User;

import java.util.List;
import java.util.Map;

/**
 * 平台用户表（{@code users}）数据访问接口。
 *
 * <p>对应接口文档：{@code POST /api/auth/login}（登录）、
 * {@code GET /api/users}（管理端用户列表）。
 *
 * <h3>安全约定（课程要求「安全 +2 分」）</h3>
 * <ul>
 *   <li>{@link #findByUsernameForAuth} 是<b>唯一</b>会返回 {@code passwordHash} 的方法，
 *       命名中显式标注 {@code ForAuth}，提醒调用方不得把结果直接序列化返回；</li>
 *   <li>其余查询方法一律<b>不查口令列</b>，从 SQL 层面杜绝口令外泄；</li>
 *   <li>口令比对由 Service 调用 {@code PasswordUtil.verify} 完成，
 *       DAO 不做任何口令运算，也不支持"按口令查询"。</li>
 * </ul>
 *
 * @author 追风者项目组
 * @see com.zfz.typhoon.util.PasswordUtil
 */
public interface UserDao {

    /** 角色：平台总管理员 */
    String ROLE_ADMIN = "ADMIN";

    /** 角色：普通用户 */
    String ROLE_USER = "USER";

    /* ------------------------------------------------------------------ */
    /*  查询                                                               */
    /* ------------------------------------------------------------------ */

    /**
     * 按用户名查询<b>含口令哈希</b>的用户（仅供登录鉴权使用）。
     *
     * @param username 登录用户名
     * @return 用户（含 passwordHash）；不存在返回 null
     */
    User findByUsernameForAuth(String username);

    /**
     * 按用户名查询用户（<b>不含</b>口令哈希）。
     *
     * @param username 登录用户名
     * @return 用户；不存在返回 null
     */
    User findByUsername(String username);

    /**
     * 按主键查询用户（<b>不含</b>口令哈希）。
     *
     * @param userId 用户ID
     * @return 用户；不存在返回 null
     */
    User findById(long userId);

    /**
     * 查询全部用户（按创建时间倒序）。
     *
     * @return 用户列表
     */
    List<User> listAll();

    /**
     * 按角色查询用户。
     *
     * @param role ADMIN / USER
     * @return 用户列表
     */
    List<User> listByRole(String role);

    /**
     * 多条件检索用户（管理端：关键词 + 角色 + 状态）。
     *
     * @param keyword 关键词（用户名 / 昵称 / 邮箱），可为空
     * @param role    角色，可为 null
     * @param enabled 启用状态，可为 null
     * @return 用户列表
     */
    List<User> search(String keyword, String role, Boolean enabled);

    /**
     * 判断用户名是否已被占用（注册前置校验）。
     *
     * @param username 用户名
     * @return 已存在返回 true
     */
    boolean existsUsername(String username);

    /* ------------------------------------------------------------------ */
    /*  统计                                                               */
    /* ------------------------------------------------------------------ */

    /**
     * 统计用户总数。
     *
     * @return 总数
     */
    int countAll();

    /**
     * 按角色统计。
     *
     * @param role ADMIN / USER
     * @return 数量
     */
    int countByRole(String role);

    /**
     * 统计启用中的用户数。
     *
     * @return 数量
     */
    int countEnabled();

    /**
     * 按注册日期分组统计每日新增用户数（升序）。
     *
     * <p>供后台 ECharts「用户增长趋势」折线图使用。
     * 返回累计口径由服务层计算，DAO 只负责按 {@code DATE(created_at)} 分组计数。
     *
     * @return [{date: "2026-09-01", count: 3}, ...]，无数据返回空列表
     */
    List<Map<String, Object>> countByCreatedDate();

    /* ------------------------------------------------------------------ */
    /*  写入                                                               */
    /* ------------------------------------------------------------------ */

    /**
     * 新增用户。
     *
     * @param user 用户（{@code passwordHash} 必须已由 PasswordUtil 加密）
     * @return 新生成的主键
     */
    long insert(User user);

    /**
     * 更新用户基础资料（昵称、邮箱、手机号），<b>不含</b>口令与角色。
     *
     * @param user 用户（必须含 userId）
     * @return 受影响行数
     */
    int updateProfile(User user);

    /**
     * 更新密码哈希。
     *
     * @param userId       用户ID
     * @param passwordHash 新的口令哈希
     * @return 受影响行数
     */
    int updatePassword(long userId, String passwordHash);

    /**
     * 记录一次成功登录（刷新 {@code last_login}）。
     *
     * <p>时间由数据库的 {@code NOW()} 产生，不接收 Java 侧时间戳 ——
     * 避免应用服务器与数据库时钟不一致时把"最后登录时间"写成未来。
     *
     * @param userId 用户ID
     * @return 受影响行数
     */
    int updateLastLogin(long userId);

    /**
     * 启用 / 禁用用户。
     *
     * @param userId  用户ID
     * @param enabled 是否启用
     * @return 受影响行数
     */
    int setEnabled(long userId, boolean enabled);

    /**
     * 变更用户角色。
     *
     * @param userId 用户ID
     * @param role   ADMIN / USER
     * @return 受影响行数
     */
    int setRole(long userId, String role);

    /**
     * 按主键删除用户（外键级联会清理其通知、订阅、收藏等）。
     *
     * @param userId 用户ID
     * @return 受影响行数
     */
    int deleteById(long userId);
}
