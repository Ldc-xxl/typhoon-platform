package com.zfz.typhoon.dao.impl;

import com.zfz.typhoon.dao.UserSubscriptionDao;
import com.zfz.typhoon.model.UserSubscription;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 用户订阅表 DAO 实现。
 *
 * <p>布尔列（{@code notify_*} / {@code push_enabled}）在设计上是
 * {@code tinyint NOT NULL DEFAULT 1}，因此插入时若上层传 null，
 * 这里统一按 {@code true}（默认开启）处理，与 DDL 默认值保持一致——
 * 否则会出现"数据库默认开、但应用写入 0"的不一致。
 *
 * @author 追风者项目组
 */
public class UserSubscriptionDaoImpl implements UserSubscriptionDao {

    private static final String COLUMNS =
            "s.subscription_id, s.user_id, s.typhoon_id, s.city_id, s.notify_new_storm, "
                    + "s.notify_strength_change, s.notify_path_change, s.push_enabled, s.created_at";

    private static final RowMapper<UserSubscription> MAPPER = new RowMapper<UserSubscription>() {
        @Override
        public UserSubscription map(ResultSet rs) throws SQLException {
            UserSubscription s = new UserSubscription();
            s.setSubscriptionId(RsUtil.getLong(rs, "subscription_id"));
            s.setUserId(RsUtil.getLong(rs, "user_id"));
            s.setTyphoonId(RsUtil.getLong(rs, "typhoon_id"));
            s.setCityId(RsUtil.getLong(rs, "city_id"));
            s.setNotifyNewStorm(RsUtil.getBoolean(rs, "notify_new_storm"));
            s.setNotifyStrengthChange(RsUtil.getBoolean(rs, "notify_strength_change"));
            s.setNotifyPathChange(RsUtil.getBoolean(rs, "notify_path_change"));
            s.setPushEnabled(RsUtil.getBoolean(rs, "push_enabled"));
            s.setCreatedAt(RsUtil.getDateTime(rs, "created_at"));
            return s;
        }
    };

    private static final String INSERT_SQL =
            "INSERT INTO user_subscriptions (user_id, typhoon_id, city_id, notify_new_storm, "
                    + "notify_strength_change, notify_path_change, push_enabled) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    @Override
    public List<UserSubscription> listByUser(long userId) {
        return JdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM user_subscriptions s WHERE s.user_id = ? "
                        + "ORDER BY s.created_at DESC, s.subscription_id DESC",
                MAPPER, Long.valueOf(userId));
    }

    @Override
    public List<UserSubscription> listByTyphoon(long typhoonId) {
        // 命中 idx_sub_typhoon
        return JdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM user_subscriptions s WHERE s.typhoon_id = ? "
                        + "ORDER BY s.subscription_id ASC",
                MAPPER, Long.valueOf(typhoonId));
    }

    @Override
    public List<UserSubscription> listByCity(long cityId) {
        // 命中 idx_sub_city
        return JdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM user_subscriptions s WHERE s.city_id = ? "
                        + "ORDER BY s.subscription_id ASC",
                MAPPER, Long.valueOf(cityId));
    }

    @Override
    public UserSubscription findById(long subscriptionId) {
        String sql = "SELECT " + COLUMNS + " FROM user_subscriptions s WHERE s.subscription_id = ?";
        return JdbcTemplate.queryOne(sql, MAPPER, Long.valueOf(subscriptionId));
    }

    @Override
    public UserSubscription findByUserAndTyphoon(long userId, long typhoonId) {
        String sql = "SELECT " + COLUMNS + " FROM user_subscriptions s "
                + "WHERE s.user_id = ? AND s.typhoon_id = ? LIMIT 1";
        return JdbcTemplate.queryOne(sql, MAPPER, Long.valueOf(userId), Long.valueOf(typhoonId));
    }

    @Override
    public UserSubscription findByUserAndCity(long userId, long cityId) {
        String sql = "SELECT " + COLUMNS + " FROM user_subscriptions s "
                + "WHERE s.user_id = ? AND s.city_id = ? LIMIT 1";
        return JdbcTemplate.queryOne(sql, MAPPER, Long.valueOf(userId), Long.valueOf(cityId));
    }

    @Override
    public int countByUser(long userId) {
        Integer n = JdbcTemplate.queryInt(
                "SELECT COUNT(*) FROM user_subscriptions WHERE user_id = ?", Long.valueOf(userId));
        return n == null ? 0 : n.intValue();
    }

    @Override
    public int countByTyphoon(long typhoonId) {
        Integer n = JdbcTemplate.queryInt(
                "SELECT COUNT(*) FROM user_subscriptions WHERE typhoon_id = ?", Long.valueOf(typhoonId));
        return n == null ? 0 : n.intValue();
    }

    @Override
    public long insert(UserSubscription s) {
        Long id = JdbcTemplate.insert(INSERT_SQL,
                s.getUserId(), s.getTyphoonId(), s.getCityId(),
                defaultTrue(s.getNotifyNewStorm()),
                defaultTrue(s.getNotifyStrengthChange()),
                defaultTrue(s.getNotifyPathChange()),
                defaultTrue(s.getPushEnabled()));
        return id == null ? 0L : id.longValue();
    }

    @Override
    public int updatePreferences(UserSubscription s) {
        String sql = "UPDATE user_subscriptions SET notify_new_storm = ?, "
                + "notify_strength_change = ?, notify_path_change = ?, push_enabled = ? "
                + "WHERE subscription_id = ?";
        return JdbcTemplate.update(sql,
                defaultTrue(s.getNotifyNewStorm()),
                defaultTrue(s.getNotifyStrengthChange()),
                defaultTrue(s.getNotifyPathChange()),
                defaultTrue(s.getPushEnabled()),
                s.getSubscriptionId());
    }

    @Override
    public int setPushEnabledForUser(long userId, boolean enabled) {
        return JdbcTemplate.update(
                "UPDATE user_subscriptions SET push_enabled = ? WHERE user_id = ?",
                Boolean.valueOf(enabled), Long.valueOf(userId));
    }

    @Override
    public int deleteByIdAndUser(long subscriptionId, long userId) {
        return JdbcTemplate.update(
                "DELETE FROM user_subscriptions WHERE subscription_id = ? AND user_id = ?",
                Long.valueOf(subscriptionId), Long.valueOf(userId));
    }

    @Override
    public int deleteByUserAndTyphoon(long userId, long typhoonId) {
        return JdbcTemplate.update(
                "DELETE FROM user_subscriptions WHERE user_id = ? AND typhoon_id = ?",
                Long.valueOf(userId), Long.valueOf(typhoonId));
    }

    /**
     * null 视为开启（与 DDL 的 {@code DEFAULT 1} 保持一致）。
     *
     * @param v 布尔值
     * @return 非空布尔值
     */
    private static Boolean defaultTrue(Boolean v) {
        return Boolean.valueOf(v == null || v.booleanValue());
    }
}
