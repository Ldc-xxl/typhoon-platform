package com.zfz.typhoon.service;

import com.zfz.typhoon.model.User;
import com.zfz.typhoon.model.vo.LoginVO;
import com.zfz.typhoon.model.vo.UserVO;

/**
 * 认证业务接口。
 *
 * <p>对应接口文档：{@code POST /api/auth/login}（登录）与
 * {@code /api/auth/logout}、{@code /api/auth/me}（会话）。
 *
 * <h3>为什么认证与用户管理分成两个服务</h3>
 * {@link AuthService} 只负责「证明你是谁」，{@link UserService} 只负责「如何管理用户」。
 * 两者共用 {@code users} 表但职责和权限边界完全不同：
 * 认证接口对匿名开放（否则没人能登录），用户管理接口必须管理员才能访问。
 * 拆开后，Servlet 层的鉴权规则可以一一对应到服务，
 * 不会出现"某个用户管理方法顺手被匿名接口调到"的漏洞。
 *
 * <h3>口令处理</h3>
 * 全程只处理明文口令的<b>哈希比对</b>，任何环节都不存储、不返回明文。
 * 比对由 {@link com.zfz.typhoon.util.PasswordUtil#verify} 完成。
 *
 * @author 追风者项目组
 */
public interface AuthService {

    /**
     * 校验账号口令并生成登录凭据。
     *
     * @param username 登录名
     * @param password 明文口令
     * @return 登录凭据（token / role / userName）
     * @throws com.zfz.typhoon.util.BusinessException 账号不存在、口令错误或账号被停用时抛出
     */
    LoginVO login(String username, String password);

    /**
     * 注册新账号（注册即可用：调用方拿到凭据后直接为其建立登录会话）。
     *
     * <p>与 {@link #login} 的关键区别：本方法<b>不做任何口令比对</b>，
     * 而是把明文口令交给 {@link com.zfz.typhoon.util.PasswordUtil#hash} 生成
     * 带随机盐的摘要后落库。请求体里即使带了 {@code role} 也会被忽略，
     * 新账号角色恒为 {@code USER} —— 否则任何人都能自助注册出一个管理员。
     *
     * <p>用户名查重复用 {@link com.zfz.typhoon.dao.UserDao#existsUsername}，
     * 落库复用 {@link com.zfz.typhoon.dao.UserDao#insert}（与管理端建号同一条写入路径），
     * 不新造第二份插入逻辑。
     *
     * @param username 登录名（必填，3—50 位，仅字母/数字/下划线）
     * @param password 明文口令（必填，6—64 位）
     * @param nickname 昵称（选填，最长 50；为空时回退为登录名）
     * @param email    邮箱（选填，最长 100；非空时校验基本格式）
     * @return 登录凭据（含 userId / role / userName 等，与 {@link #login} 同形）
     * @throws com.zfz.typhoon.util.BusinessException 参数不合法（400）或用户名已被占用（409）
     */
    LoginVO register(String username, String password, String nickname, String email);

    /**
     * 取当前登录用户的基本信息（供顶栏与刷新页面后恢复会话）。
     *
     * @param userId 用户ID
     * @return 用户视图
     */
    UserVO profile(long userId);

    /**
     * 修改本人密码（需校验原密码）。
     *
     * @param userId      用户ID
     * @param oldPassword 原密码
     * @param newPassword 新密码
     */
    void changePassword(long userId, String oldPassword, String newPassword);

    /**
     * 依据登录名取用户实体（供过滤器做权限判断）。
     *
     * @param username 登录名
     * @return 用户；不存在返回 null
     */
    User findByUsername(String username);

    /**
     * 清除某账号的登录失败计数（管理员解锁用）。
     *
     * @param username 登录名
     */
    void unlock(String username);
}
