package com.zfz.typhoon.dao;

import com.zfz.typhoon.model.LoginAttempt;

import java.time.LocalDateTime;

/**
 * 登录失败锁定状态表（{@code login_attempts}）数据访问接口。
 *
 * <p>只服务于「口令爆破防护」这一件事，因此刻意保持极小的接口面：
 * 查一条、写一条、删一条。
 *
 * <h3>关于时区</h3>
 * 读写统一用 {@link LocalDateTime}，与 {@code users.created_at} 等
 * 已有列保持一致的口径；比较"是否已过锁定时间"由调用方
 * （{@code AuthServiceImpl}）用 {@code System.currentTimeMillis()} 完成，
 * DAO 不做时间判断，避免把业务规则下沉到数据层。
 *
 * @author 追风者项目组
 * @see com.zfz.typhoon.model.LoginAttempt
 */
public interface LoginAttemptDao {

    /**
     * 按登录名查询锁定状态。
     *
     * @param username 登录名（已裁剪空白）
     * @return 锁定状态行；该账号从未失败过时返回 {@code null}
     */
    LoginAttempt findByUsername(String username);

    /**
     * 写入（或更新）一条失败记录。
     *
     * <p>实现使用 {@code INSERT ... ON DUPLICATE KEY UPDATE}：
     * 首次失败插入、后续失败更新，调用方无需先查后判，
     * 也避免了"查-改-写"之间的并发窗口。
     *
     * @param username    登录名
     * @param failCount   当前连续失败次数（触发锁定后应传 0）
     * @param lastFailAt  本次失败时间
     * @param lockedUntil 锁定截止时间；未触发锁定传 {@code null}
     * @return 受影响行数（1 表示写入成功）
     */
    int save(String username, int failCount, LocalDateTime lastFailAt, LocalDateTime lockedUntil);

    /**
     * 清除某个账号的失败记录（登录成功或管理员手动解锁时调用）。
     *
     * @param username 登录名
     * @return 受影响行数（0 表示本来就没有记录，属正常情况）
     */
    int delete(String username);
}
