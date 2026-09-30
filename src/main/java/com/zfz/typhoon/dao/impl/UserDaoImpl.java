package com.zfz.typhoon.dao.impl;

import com.zfz.typhoon.dao.UserDao;
import com.zfz.typhoon.model.User;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;
import com.zfz.typhoon.util.SqlBuilder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台用户表 DAO 实现。
 *
 * <h3>口令列的最小暴露原则</h3>
 * {@link #findByUsernameForAuth} 使用 {@link #COLUMNS_WITH_PASSWORD}，
 * 其余方法统一使用 {@link #COLUMNS_WITHOUT_PASSWORD}。
 * 这样即使上层代码不小心把查询结果直接 {@code JsonUtil.write} 返回，
 * 也不会泄露口令哈希——防御写在数据访问层，而不是依赖调用方自觉。
 *
 * @author 追风者项目组
 */
public class UserDaoImpl implements UserDao {

    /** 不含口令的列清单（绝大多数查询使用） */
    private static final String COLUMNS_WITHOUT_PASSWORD =
            "u.user_id, u.username, u.email, u.phone, u.nickname, u.role, u.status, "
                    + "u.created_at, u.updated_at, u.last_login";

    /** 含口令的列清单（仅登录鉴权使用，方法名带 ForAuth 以示警戒） */
    private static final String COLUMNS_WITH_PASSWORD = COLUMNS_WITHOUT_PASSWORD + ", u.password_hash";

    /** 通用映射器：口令缺失时 passwordHash 为 null */
    private static final RowMapper<User> MAPPER = new RowMapper<User>() {
        @Override
        public User map(ResultSet rs) throws SQLException {
            User u = new User();
            u.setUserId(RsUtil.getLong(rs, "user_id"));
            u.setUsername(RsUtil.getString(rs, "username"));
            u.setEmail(RsUtil.getString(rs, "email"));
            u.setPhone(RsUtil.getString(rs, "phone"));
            u.setNickname(RsUtil.getString(rs, "nickname"));
            u.setRole(RsUtil.getString(rs, "role"));
            u.setStatus(RsUtil.getBoolean(rs, "status"));
            u.setCreatedAt(RsUtil.getDateTime(rs, "created_at"));
            u.setUpdatedAt(RsUtil.getDateTime(rs, "updated_at"));
            u.setLastLogin(RsUtil.getDateTime(rs, "last_login"));
            // 无口令列的查询会抛异常，故先探测列是否存在
            try {
                u.setPasswordHash(RsUtil.getString(rs, "password_hash"));
            } catch (SQLException ignored) {
                u.setPasswordHash(null);
            }
            return u;
        }
    };

    private static final String INSERT_SQL =
            "INSERT INTO users (username, password_hash, email, phone, nickname, role, status) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    /* ------------------------------------------------------------------ */
    /*  查询                                                               */
    /* ------------------------------------------------------------------ */

    @Override
    public User findByUsernameForAuth(String username) {
        // 命中唯一键 uk_users_username
        String sql = "SELECT " + COLUMNS_WITH_PASSWORD + " FROM users u WHERE u.username = ?";
        return JdbcTemplate.queryOne(sql, MAPPER, username);
    }

    @Override
    public User findByUsername(String username) {
        String sql = "SELECT " + COLUMNS_WITHOUT_PASSWORD + " FROM users u WHERE u.username = ?";
        return JdbcTemplate.queryOne(sql, MAPPER, username);
    }

    @Override
    public User findById(long userId) {
        String sql = "SELECT " + COLUMNS_WITHOUT_PASSWORD + " FROM users u WHERE u.user_id = ?";
        return JdbcTemplate.queryOne(sql, MAPPER, Long.valueOf(userId));
    }

    @Override
    public List<User> listAll() {
        return JdbcTemplate.query(
                "SELECT " + COLUMNS_WITHOUT_PASSWORD + " FROM users u "
                        + "ORDER BY u.created_at DESC, u.user_id DESC", MAPPER);
    }

    @Override
    public List<User> listByRole(String role) {
        // 命中 idx_users_role
        return JdbcTemplate.query(
                "SELECT " + COLUMNS_WITHOUT_PASSWORD + " FROM users u WHERE u.role = ? "
                        + "ORDER BY u.user_id ASC", MAPPER, role);
    }

    @Override
    public List<User> search(String keyword, String role, Boolean enabled) {
        SqlBuilder sb = SqlBuilder.select(COLUMNS_WITHOUT_PASSWORD).from("users u")
                .eq("u.role", role)
                .eqBool("u.status", enabled);
        if (keyword != null && !keyword.trim().isEmpty()) {
            String k = keyword.trim();
            // 用户名走唯一索引前缀匹配，昵称 / 邮箱为辅助条件
            sb.raw("(u.username LIKE ? OR u.nickname LIKE ? OR u.email LIKE ?)",
                    k + "%", "%" + k + "%", "%" + k + "%");
        }
        sb.orderBy("u.created_at DESC, u.user_id DESC");
        SqlBuilder.Built built = sb.build();
        return JdbcTemplate.query(built.getSql(), MAPPER, built.getParams());
    }

    @Override
    public boolean existsUsername(String username) {
        Integer n = JdbcTemplate.queryInt(
                "SELECT COUNT(*) FROM users WHERE username = ?", username);
        return n != null && n.intValue() > 0;
    }

    /* ------------------------------------------------------------------ */
    /*  统计                                                               */
    /* ------------------------------------------------------------------ */

    @Override
    public int countAll() {
        Integer n = JdbcTemplate.queryInt("SELECT COUNT(*) FROM users");
        return n == null ? 0 : n.intValue();
    }

    @Override
    public int countByRole(String role) {
        Integer n = JdbcTemplate.queryInt("SELECT COUNT(*) FROM users WHERE role = ?", role);
        return n == null ? 0 : n.intValue();
    }

    @Override
    public int countEnabled() {
        Integer n = JdbcTemplate.queryInt("SELECT COUNT(*) FROM users WHERE status = 1");
        return n == null ? 0 : n.intValue();
    }

    @Override
    public List<Map<String, Object>> countByCreatedDate() {
        String sql = "SELECT DATE(created_at) AS d, COUNT(*) AS c "
                + "FROM users GROUP BY DATE(created_at) ORDER BY d ASC";
        return JdbcTemplate.query(sql, new RowMapper<Map<String, Object>>() {
            @Override
            public Map<String, Object> map(ResultSet rs) throws SQLException {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("date", RsUtil.getString(rs, "d"));
                row.put("count", RsUtil.getInt(rs, "c"));
                return row;
            }
        });
    }

    /* ------------------------------------------------------------------ */
    /*  写入                                                               */
    /* ------------------------------------------------------------------ */

    @Override
    public long insert(User u) {
        Long id = JdbcTemplate.insert(INSERT_SQL,
                u.getUsername(), u.getPasswordHash(), u.getEmail(), u.getPhone(),
                u.getNickname() == null ? u.getUsername() : u.getNickname(),
                u.getRole() == null ? ROLE_USER : u.getRole(),
                Boolean.valueOf(u.getStatus() == null || u.getStatus().booleanValue()));
        return id == null ? 0L : id.longValue();
    }

    @Override
    public int updateProfile(User u) {
        // 刻意不含 role / password_hash / status：这三项有独立方法，避免越权改
        String sql = "UPDATE users SET email = ?, phone = ?, nickname = ? WHERE user_id = ?";
        return JdbcTemplate.update(sql, u.getEmail(), u.getPhone(), u.getNickname(), u.getUserId());
    }

    @Override
    public int updatePassword(long userId, String passwordHash) {
        return JdbcTemplate.update(
                "UPDATE users SET password_hash = ? WHERE user_id = ?",
                passwordHash, Long.valueOf(userId));
    }

    @Override
    public int updateLastLogin(long userId) {
        // 用 NOW() 而不是把时间从 Java 传进来：以数据库时钟为准，
        // 避免应用服务器与数据库服务器时钟漂移导致"最后登录时间在未来"。
        return JdbcTemplate.update(
                "UPDATE users SET last_login = NOW() WHERE user_id = ?", Long.valueOf(userId));
    }

    @Override
    public int setEnabled(long userId, boolean enabled) {
        return JdbcTemplate.update(
                "UPDATE users SET status = ? WHERE user_id = ?",
                Boolean.valueOf(enabled), Long.valueOf(userId));
    }

    @Override
    public int setRole(long userId, String role) {
        return JdbcTemplate.update(
                "UPDATE users SET role = ? WHERE user_id = ?", role, Long.valueOf(userId));
    }

    @Override
    public int deleteById(long userId) {
        return JdbcTemplate.update("DELETE FROM users WHERE user_id = ?", Long.valueOf(userId));
    }
}
