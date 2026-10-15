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
 * <h3>为什么写入用 UPSERT 而不是「先查后插」</h3>
 * 登录是高频且可并发的操作。若先 {@code SELECT} 判断有没有记录再决定
 * INSERT 或 UPDATE，两次调用之间存在并发窗口：两个请求同时判定"无记录"，
 * 就会有一个因主键冲突失败，失败次数被静默丢掉，
 * 攻击者只要并发发请求就能让计数器永远加不上去——防护等于失效。
 * 因此这里用 {@code INSERT ... ON DUPLICATE KEY UPDATE}，
 * 把判断交给数据库，一条语句搞定且原子。
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

    private static final String UPSERT_SQL =
            "INSERT INTO login_attempts (username, fail_count, last_fail_at, locked_until) "
                    + "VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "fail_count = VALUES(fail_count), "
                    + "last_fail_at = VALUES(last_fail_at), "
                    + "locked_until = VALUES(locked_until)";

    private static final String DELETE_SQL =
            "DELETE FROM login_attempts WHERE username = ?";

    @Override
    public LoginAttempt findByUsername(String username) {
        return JdbcTemplate.queryOne(SELECT_SQL, MAPPER, username);
    }

    @Override
    public int save(String username, int failCount, LocalDateTime lastFailAt, LocalDateTime lockedUntil) {
        return JdbcTemplate.update(UPSERT_SQL,
                username, Integer.valueOf(failCount), lastFailAt, lockedUntil);
    }

    @Override
    public int delete(String username) {
        return JdbcTemplate.update(DELETE_SQL, username);
    }
}
