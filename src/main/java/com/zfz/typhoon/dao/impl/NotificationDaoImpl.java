package com.zfz.typhoon.dao.impl;

import com.zfz.typhoon.dao.NotificationDao;
import com.zfz.typhoon.model.Notification;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;
import com.zfz.typhoon.util.SqlBuilder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 通知消息表 DAO 实现。
 *
 * @author 追风者项目组
 */
public class NotificationDaoImpl implements NotificationDao {

    private static final String COLUMNS =
            "n.notification_id, n.user_id, n.typhoon_id, n.city_id, n.notify_type, "
                    + "n.title, n.content, n.is_read, n.created_at";

    private static final RowMapper<Notification> MAPPER = new RowMapper<Notification>() {
        @Override
        public Notification map(ResultSet rs) throws SQLException {
            Notification n = new Notification();
            n.setNotificationId(RsUtil.getLong(rs, "notification_id"));
            n.setUserId(RsUtil.getLong(rs, "user_id"));
            n.setTyphoonId(RsUtil.getLong(rs, "typhoon_id"));
            n.setCityId(RsUtil.getLong(rs, "city_id"));
            n.setNotifyType(RsUtil.getString(rs, "notify_type"));
            n.setTitle(RsUtil.getString(rs, "title"));
            n.setContent(RsUtil.getString(rs, "content"));
            n.setRead(RsUtil.getBoolean(rs, "is_read"));
            n.setCreatedAt(RsUtil.getDateTime(rs, "created_at"));
            return n;
        }
    };

    private static final String INSERT_SQL =
            "INSERT INTO notifications (user_id, typhoon_id, city_id, notify_type, title, content, is_read) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    @Override
    public List<Notification> listByUser(long userId, int limit) {
        SqlBuilder sb = SqlBuilder.select(COLUMNS).from("notifications n")
                .eq("n.user_id", Long.valueOf(userId))
                .orderBy("n.created_at DESC, n.notification_id DESC");
        if (limit > 0) {
            sb.limit(limit, 0);
        }
        SqlBuilder.Built built = sb.build();
        return JdbcTemplate.query(built.getSql(), MAPPER, built.getParams());
    }

    @Override
    public List<Notification> listUnreadByUser(long userId) {
        // 命中 idx_notif_user_read (user_id, is_read)
        String sql = "SELECT " + COLUMNS + " FROM notifications n "
                + "WHERE n.user_id = ? AND n.is_read = 0 "
                + "ORDER BY n.created_at DESC, n.notification_id DESC";
        return JdbcTemplate.query(sql, MAPPER, Long.valueOf(userId));
    }

    @Override
    public Notification findById(long notificationId) {
        String sql = "SELECT " + COLUMNS + " FROM notifications n WHERE n.notification_id = ?";
        return JdbcTemplate.queryOne(sql, MAPPER, Long.valueOf(notificationId));
    }

    @Override
    public int countUnread(long userId) {
        // 覆盖索引扫描：不需要回表取 title/content
        Integer n = JdbcTemplate.queryInt(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = 0",
                Long.valueOf(userId));
        return n == null ? 0 : n.intValue();
    }

    @Override
    public int countByUser(long userId) {
        Integer n = JdbcTemplate.queryInt(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ?", Long.valueOf(userId));
        return n == null ? 0 : n.intValue();
    }

    @Override
    public long insert(Notification n) {
        Long id = JdbcTemplate.insert(INSERT_SQL, paramsOf(n));
        return id == null ? 0L : id.longValue();
    }

    @Override
    public int batchInsert(List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return 0;
        }
        List<Object[]> rows = new ArrayList<Object[]>(notifications.size());
        for (Notification n : notifications) {
            rows.add(paramsOf(n));
        }
        return JdbcTemplate.batchUpdate(INSERT_SQL, rows);
    }

    @Override
    public int markRead(long notificationId, long userId) {
        // 双条件：既限定通知ID，又限定归属用户，防越权
        return JdbcTemplate.update(
                "UPDATE notifications SET is_read = 1 WHERE notification_id = ? AND user_id = ?",
                Long.valueOf(notificationId), Long.valueOf(userId));
    }

    @Override
    public int markAllRead(long userId) {
        return JdbcTemplate.update(
                "UPDATE notifications SET is_read = 1 WHERE user_id = ? AND is_read = 0",
                Long.valueOf(userId));
    }

    @Override
    public int deleteByUser(long userId) {
        return JdbcTemplate.update(
                "DELETE FROM notifications WHERE user_id = ?", Long.valueOf(userId));
    }

    /**
     * 摊平参数（顺序与 INSERT_SQL 一致）；新通知默认未读。
     *
     * @param n 通知
     * @return 参数数组
     */
    private static Object[] paramsOf(Notification n) {
        return new Object[]{
                n.getUserId(), n.getTyphoonId(), n.getCityId(), n.getNotifyType(),
                n.getTitle(), n.getContent(),
                Boolean.valueOf(n.getRead() != null && n.getRead().booleanValue())
        };
    }
}
