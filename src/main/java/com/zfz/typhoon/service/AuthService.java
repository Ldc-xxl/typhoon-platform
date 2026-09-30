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
