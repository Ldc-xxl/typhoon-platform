package com.zfz.typhoon.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 平台用户实体（对应表 {@code users}）。
 *
 * <p>系统共两类角色（项目描述文档 · 系统角色划分）：
 * ADMIN = 平台总管理员，USER = 普通用户。
 *
 * <p><b>安全要求</b>：{@link #passwordHash} 标注 {@link JsonIgnore}，
 * 确保即使本实体被直接序列化也不会泄露口令摘要到前端。
 * 对外一律使用 {@code com.zfz.typhoon.model.vo.UserVO}。
 *
 * @author 追风者项目组
 */
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 角色：平台总管理员 */
    public static final String ROLE_ADMIN = "ADMIN";
    /** 角色：普通用户 */
    public static final String ROLE_USER = "USER";

    /** 用户ID（主键，自增） */
    private Long userId;

    /** 登录用户名（唯一） */
    private String username;

    /** 密码哈希值；禁止序列化输出 */
    @JsonIgnore
    private String passwordHash;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 昵称 */
    private String nickname;

    /** 角色：ADMIN / USER */
    private String role;

    /** 状态：true=正常，false=禁用 */
    private Boolean status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /**
     * 最后登录时间。
     *
     * <p>对应 {@code users.last_login} 列，由 {@code AuthServiceImpl.login()}
     * 在登录成功后写入。与 {@link #updatedAt} 语义分开：后者会被任何一次
     * 「改资料 / 改口令 / 启停用」刷新，拿它冒充登录时间会在管理后台显示成
     * 「刚登录过」——原实现正是这么兜底的，已修正。
     */
    private LocalDateTime lastLogin;

    public User() {
    }

    /**
     * 是否为管理员。
     *
     * @return 管理员返回 true
     */
    public boolean isAdmin() {
        return ROLE_ADMIN.equalsIgnoreCase(role);
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }

    @Override
    public String toString() {
        return "User{" + username + ", role=" + role + '}';
    }
}
