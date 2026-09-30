package com.zfz.typhoon.model.vo;

import com.zfz.typhoon.model.User;
import com.zfz.typhoon.util.DateUtil;

import java.io.Serializable;

/**
 * 用户视图对象（面向前端 P8 管理后台用户表）。
 *
 * <p>字段名对齐前端 {@code ZFZ.mock.users}，使后台表格在关闭 mock 后无需改渲染代码。
 * 其中 {@link #scope} 是"权限范围"的中文描述，
 * 由后端按角色推导（管理员含系统配置、普通用户只有查询/沙盘/问卷），
 * 避免前端硬编码角色权限表。
 *
 * <p><b>安全</b>：不包含 {@code password_hash}。实现自
 * {@link com.zfz.typhoon.model.User}，该实体的口令字段已标 {@code @JsonIgnore}，
 * 本 VO 更是完全不携带。
 *
 * @author 追风者项目组
 */
public class UserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID（前端操作按钮的锚点） */
    private Long id;

    /** 登录账号 */
    private String account;

    /** 昵称 */
    private String nickname;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 角色中文名：平台总管理员 / 普通用户 */
    private String role;

    /** 角色代码：ADMIN / USER */
    private String roleCode;

    /** 权限范围描述 */
    private String scope;

    /** 注册日期（yyyy-MM-dd） */
    private String registered;

    /** 最后登录时间（yyyy-MM-dd HH:mm） */
    private String lastLogin;
    /** 状态中文：正常 / 已停用 */
    private String status;

    /** 状态代码：true=正常 */
    private Boolean enabled;

    /** 头像字符 */
    private String avatarText;

    /**
     * 操作列按钮文案，以 {@code ' · '} 连接。
     *
     * <p>前端 {@code admin.js} 直接用 {@code u.ops.split(' · ')} 渲染按钮，
     * 因此这里必须输出同一种分隔符；缺失会导致整行操作按钮消失。
     * 取值由后端按角色与状态推导（管理员可重置密码、普通用户可禁用/启用），
     * 避免前端硬编码一份权限表、与后端鉴权规则各写一套。
     */
    private String ops;

    public UserVO() {
    }

    /**
     * 由实体组装。
     *
     * @param user      用户实体
     * @param lastLogin 最后登录时间；传 null 时回退到 {@link User#getLastLogin()}
     * @return 视图对象
     */
    public static UserVO of(User user, java.time.LocalDateTime lastLogin) {
        UserVO vo = new UserVO();
        if (user == null) {
            return vo;
        }
        vo.id = user.getUserId();
        vo.account = user.getUsername();
        vo.nickname = user.getNickname();
        vo.email = user.getEmail();
        vo.phone = user.getPhone();
        vo.roleCode = user.getRole();
        boolean admin = user.isAdmin();
        vo.role = admin ? "平台总管理员" : "普通用户";
        vo.scope = admin ? "全部功能 + 系统配置" : "查询 / 沙盘 / 问卷";
        vo.registered = user.getCreatedAt() == null ? null : DateUtil.format(user.getCreatedAt().toLocalDate());
        // 只认真正的登录时间。此前用 user.updated_at 兜底是错的：
        // 改一次昵称、重置一次口令都会刷新 updated_at，后台就会显示成"刚登录过"。
        // 现在 users 表有 last_login 列，从未登录过就如实留空，前端显示"—"。
        java.time.LocalDateTime last = lastLogin != null ? lastLogin : user.getLastLogin();
        vo.lastLogin = DateUtil.format(last);
        vo.enabled = user.getStatus();
        vo.status = Boolean.FALSE.equals(user.getStatus()) ? "已停用" : "正常";
        String name = user.getNickname() == null || user.getNickname().isEmpty()
                ? user.getUsername() : user.getNickname();
        vo.avatarText = name == null || name.isEmpty() ? "?" : name.substring(0, 1);
        // 操作按钮由角色 + 当前状态共同决定，与 UserService 的权限校验一一对应：
        // 管理员可重置密码，普通用户可禁用/启用（管理员账号不允许停用）
        if (admin) {
            vo.ops = "编辑 · 重置";
        } else {
            vo.ops = Boolean.FALSE.equals(user.getStatus()) ? "编辑 · 启用" : "编辑 · 禁用";
        }
        return vo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getRegistered() {
        return registered;
    }

    public void setRegistered(String registered) {
        this.registered = registered;
    }

    public String getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(String lastLogin) {
        this.lastLogin = lastLogin;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getAvatarText() {
        return avatarText;
    }

    public void setAvatarText(String avatarText) {
        this.avatarText = avatarText;
    }

    public String getOps() {
        return ops;
    }

    public void setOps(String ops) {
        this.ops = ops;
    }
}
