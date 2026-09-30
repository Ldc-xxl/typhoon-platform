package com.zfz.typhoon.model.vo;

import java.io.Serializable;

/**
 * 登录结果视图对象（面向前端登录弹层与顶栏用户区）。
 *
 * <p>字段名对齐前端契约（{@code api.js} 中 {@code login()} 的返回）：
 * {@code { token, role, userName }}。
 *
 * <p><b>安全</b>：只返回昵称与角色，绝不返回口令摘要或盐值。
 *
 * @author 追风者项目组
 */
public class LoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话令牌（写入 Session 的标识，前端存入 localStorage） */
    private String token;

    /** 角色：ADMIN / USER */
    private String role;

    /** 角色中文名 */
    private String roleText;

    /** 展示用名称（优先昵称） */
    private String userName;

    /** 登录账号 */
    private String account;

    /** 用户ID */
    private Long userId;

    /** 头像字符（取昵称首字，供顶栏圆形头像） */
    private String avatarText;

    /** 会话有效期（秒） */
    private Integer expiresIn;

    public LoginVO() {
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getRoleText() {
        return roleText;
    }

    public void setRoleText(String roleText) {
        this.roleText = roleText;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getAvatarText() {
        return avatarText;
    }

    public void setAvatarText(String avatarText) {
        this.avatarText = avatarText;
    }

    public Integer getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Integer expiresIn) {
        this.expiresIn = expiresIn;
    }
}
