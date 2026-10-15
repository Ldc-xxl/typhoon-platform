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
     * 原子地登记一次登录失败。
     *
     * <p><b>为什么这里没有"传入次数"这个参数</b>：计数递增必须在数据库里完成，
     * 而不是由调用方"先查 ← 读出 count → 算 count+1 → 再写回"。
     * 后者的三步之间是并发窗口：N 个并发错误口令请求会各自读到同一旧值、
     * 算出同一个 count、后写覆盖先写，{@code fail_count} 永远停在 1，
     * 连续失败锁定形同虚设。因此本方法把 {@code fail_count = fail_count + 1}
     * 交由一条 {@code INSERT ... ON DUPLICATE KEY UPDATE} 语句原子完成，
     * 数据库的行锁保证并发下每一次失败都被真实计入。
     *
     * <p>同时处理"失败不连续"：距上次失败超过 {@code ttlMinutes} 视为新一轮爆破，
     * 计数从 1 重新开始（该判断同样在 SQL 内完成，避免回到"先查后写"）。
     *
     * @param username  登录名
     * @param lastFailAt 本次失败时间
     * @param ttlMinutes 连续失败的时间窗（分钟），超出则重新计数
     * @return 自增后的连续失败次数（≥ 1）；调用方据此判定是否达到锁定阈值
     */
    int incrementFail(String username, LocalDateTime lastFailAt, int ttlMinutes);

    /**
     * 写入锁定截止时间，并把失败计数归零。
     *
     * <p>归零是为了避免"解锁瞬间又被立刻锁上"：用户等满锁定时长后重试时，
     * 应当从 0 次失败重新起算，而不是带着旧计数一击即锁。
     *
     * @param username    登录名
     * @param lockedUntil 锁定截止时间
     * @return 受影响行数（1 表示写入成功）
     */
    int setLocked(String username, LocalDateTime lockedUntil);

    /**
     * 清除某个账号的失败记录（登录成功或管理员手动解锁时调用）。
     *
     * @param username 登录名
     * @return 受影响行数（0 表示本来就没有记录，属正常情况）
     */
    int delete(String username);
}
