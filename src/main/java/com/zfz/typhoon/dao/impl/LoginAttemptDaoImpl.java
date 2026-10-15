package com.zfz.typhoon.dao.impl;

import com.zfz.typhoon.dao.LoginAttemptDao;
import com.zfz.typhoon.model.LoginAttempt;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * 登录失败锁定状态表（{@code login_attempts}）DAO 实现。
 *
 * <h3>为什么写入用 UPSERT，而且递增必须写在 SQL 里</h3>
 * 登录是高频且可并发的操作。若先 {@code SELECT} 判断有没有记录再决定
 * INSERT 或 UPDATE，两次调用之间存在并发窗口：两个请求同时判定"无记录"，
 * 就会有一个因主键冲突失败，失败次数被静默丢掉，
 * 攻击者只要并发发请求就能让计数器永远加不上去——防护等于失效。
 *
 * <p>因此这里用 {@code INSERT ... ON DUPLICATE KEY UPDATE}，
 * 并且<b>把 {@code fail_count = fail_count + 1} 这个自增也放进 SQL</b>：
 * 只解决"插入冲突"是不够的，递增若仍发生在 Java 侧（先查 ← 读 count →
 * 算 count+1 → 再写回），并发下 N 个请求会各自读到同一旧值、算出同一个结果、
 * 后写覆盖先写，计数永远停在 1。放进 SQL 后由数据库行锁串行化，
 * 每一次失败都被真实计入，连续失败锁定才真正生效。
 *
 * @author 追风者项目组
 * @see com.zfz.typhoon.dao.LoginAttemptDao
 */
public class LoginAttemptDaoImpl implements LoginAttemptDao {

    private static final String COLUMNS =
            "username, fail_count, last_fail_at, locked_until, updated_at";

    private static final RowMapper<LoginAttempt> MAPPER = new RowMapper<LoginAttempt>() {
        @Override
        public LoginAttempt map(ResultSet rs) throws SQLException {
            LoginAttempt a = new LoginAttempt();
            a.setUsername(RsUtil.getString(rs, "username"));
            a.setFailCount(RsUtil.getInt(rs, "fail_count"));
            a.setLastFailAt(RsUtil.getDateTime(rs, "last_fail_at"));
            a.setLockedUntil(RsUtil.getDateTime(rs, "locked_until"));
            a.setUpdatedAt(RsUtil.getDateTime(rs, "updated_at"));
            return a;
        }
    };

    private static final String SELECT_SQL =
            "SELECT " + COLUMNS + " FROM login_attempts WHERE username = ?";

    /**
     * 原子登记一次失败：不存在则插 1，存在则在 SQL 内自增。
     *
     * <p>计数与 {@code last_fail_at} 的更新在同一条语句、同一个行锁内完成，
     * 因此并发请求不会互相覆盖。三段分支：
     * <ul>
     *   <li>{@code last_fail_at IS NULL}：历史脏数据，按首次失败处理；</li>
     *   <li>距上次失败超过 TTL 秒：视为新一轮爆破，重置为 1；</li>
     *   <li>否则：{@code fail_count + 1}。</li>
     * </ul>
     * 同时清理已过期的 {@code locked_until}，避免旧锁残留影响状态判读。
     */
    private static final String INCREMENT_SQL =
            "INSERT INTO login_attempts (username, fail_count, last_fail_at, locked_until) "
                    + "VALUES (?, 1, ?, NULL) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "fail_count = IF(last_fail_at IS NULL "
                    + "               OR TIMESTAMPDIFF(SECOND, last_fail_at, VALUES(last_fail_at)) > ?, "
                    + "               1, fail_count + 1), "
                    + "last_fail_at = VALUES(last_fail_at), "
                    + "locked_until = IF(locked_until IS NULL "
                    + "                  OR locked_until <= VALUES(last_fail_at), NULL, locked_until)";

    private static final String COUNT_SQL =
            "SELECT fail_count FROM login_attempts WHERE username = ?";

    private static final String LOCK_SQL =
            "UPDATE login_attempts SET fail_count = 0, locked_until = ? WHERE username = ?";

    private static final String DELETE_SQL =
            "DELETE FROM login_attempts WHERE username = ?";

    @Override
    public LoginAttempt findByUsername(String username) {
        return JdbcTemplate.queryOne(SELECT_SQL, MAPPER, username);
    }

    @Override
    public int incrementFail(String username, LocalDateTime lastFailAt, int ttlMinutes) {
        // TTL 转秒后交给 SQL 比较；至少 1 秒，避免误传 0 导致每次都重置
        long ttlSeconds = Math.max(1L, (long) ttlMinutes * 60L);
        JdbcTemplate.update(INCREMENT_SQL, username, lastFailAt, Long.valueOf(ttlSeconds));
        // 递增已在上一条语句中原子完成，这里只是读回结果用于判定是否达到阈值。
        // 即便读回期间又有并发失败导致数值偏大，方向也是"更容易锁定"，安全。
        Integer count = JdbcTemplate.queryInt(COUNT_SQL, username);
        return count == null ? 1 : count.intValue();
    }

    @Override
    public int setLocked(String username, LocalDateTime lockedUntil) {
        return JdbcTemplate.update(LOCK_SQL, lockedUntil, username);
    }

    @Override
    public int delete(String username) {
        return JdbcTemplate.update(DELETE_SQL, username);
    }
}
