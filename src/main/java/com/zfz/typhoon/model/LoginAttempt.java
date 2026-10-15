package com.zfz.typhoon.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录失败锁定状态实体（对应表 {@code login_attempts}）。
 *
 * <p>用途：把「连续输错口令 → 临时锁定」这一状态从 JVM 内存搬到数据库，
 * 使 Tomcat 重启后锁定依然有效（课程「安全 +2 分」里的口令爆破防护）。
 *
 * <h3>为什么主键是 username 而不是 user_id</h3>
 * 攻击者常用<b>不存在的账号</b>反复尝试，若只对已存在用户计数，
 * 就等于给账号枚举开了绿灯。因此本表按登录名记账，
 * 且<b>刻意不加</b>指向 {@code users} 的外键——库里没有的名字也要能写进来。
 *
 * <h3>字段语义</h3>
 * <ul>
 *   <li>{@link #failCount} 达到阈值触发锁定后<b>归零</b>，
 *       所以「当前是否被锁」只看 {@link #lockedUntil}，不要拿 failCount 判断；</li>
 *   <li>{@link #lastFailAt} 用于判定是否属于「连续」失败：
 *       距上次失败过久（默认 30 分钟）就重新计数，避免把用户几个月前
 *       偶尔输错的一次算进爆破序列。</li>
 * </ul>
 *
 * @author 追风者项目组
 * @see com.zfz.typhoon.dao.LoginAttemptDao
 */
public class LoginAttempt implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 登录名（主键，与 {@code users.username} 同口径） */
    private String username;

    /** 连续失败次数；触发锁定后归零 */
    private Integer failCount;

    /** 最近一次失败时间；用于判定是否属于「连续」爆破 */
    private LocalDateTime lastFailAt;

    /** 锁定截止时间；{@code null} 表示未锁定 */
    private LocalDateTime lockedUntil;

    /** 记录更新时间（由数据库 ON UPDATE 维护） */
    private LocalDateTime updatedAt;

    public LoginAttempt() {
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Integer getFailCount() {
        return failCount;
    }

    public void setFailCount(Integer failCount) {
        this.failCount = failCount;
    }

    public LocalDateTime getLastFailAt() {
        return lastFailAt;
    }

    public void setLastFailAt(LocalDateTime lastFailAt) {
        this.lastFailAt = lastFailAt;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
