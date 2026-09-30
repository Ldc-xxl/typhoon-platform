package com.zfz.typhoon.dao.impl;

import com.zfz.typhoon.dao.FeedbackReportDao;
import com.zfz.typhoon.model.FeedbackReport;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;
import com.zfz.typhoon.util.SqlBuilder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户反馈表 DAO 实现。
 *
 * @author 追风者项目组
 */
public class FeedbackReportDaoImpl implements FeedbackReportDao {

    /** 联表带出提交人用户名，管理端列表可直接显示"谁提的" */
    private static final String COLUMNS =
            "f.feedback_id, f.user_id, f.feedback_type, f.content, f.contact_info, "
                    + "f.status, f.handler_id, f.handle_time, f.created_at, "
                    + "u.username AS username";

    private static final RowMapper<FeedbackReport> MAPPER = new RowMapper<FeedbackReport>() {
        @Override
        public FeedbackReport map(ResultSet rs) throws SQLException {
            FeedbackReport f = new FeedbackReport();
            f.setFeedbackId(RsUtil.getLong(rs, "feedback_id"));
            f.setUserId(RsUtil.getLong(rs, "user_id"));
            f.setFeedbackType(RsUtil.getString(rs, "feedback_type"));
            f.setContent(RsUtil.getString(rs, "content"));
            f.setContactInfo(RsUtil.getString(rs, "contact_info"));
            f.setStatus(RsUtil.getString(rs, "status"));
            f.setHandlerId(RsUtil.getLong(rs, "handler_id"));
            f.setHandleTime(RsUtil.getDateTime(rs, "handle_time"));
            f.setCreatedAt(RsUtil.getDateTime(rs, "created_at"));
            f.setUsername(RsUtil.getString(rs, "username"));
            return f;
        }
    };

    /** LEFT JOIN：{@code user_id} 允许为 NULL（未登录也可提交反馈） */
    private static final String FROM_JOIN =
            " FROM feedback_reports f LEFT JOIN users u ON u.user_id = f.user_id ";

    private static final String INSERT_SQL =
            "INSERT INTO feedback_reports (user_id, feedback_type, content, contact_info, status) "
                    + "VALUES (?, ?, ?, ?, ?)";

    @Override
    public List<FeedbackReport> list(String status, String type, int limit) {
        // 需要联表取用户名，因此 from 子句内联 JOIN，而不是用 SqlBuilder.from(单表)
        SqlBuilder full = SqlBuilder.of("SELECT " + COLUMNS)
                .from("feedback_reports f LEFT JOIN users u ON u.user_id = f.user_id")
                .eq("f.status", status)
                .eq("f.feedback_type", type)
                .orderBy("f.created_at DESC, f.feedback_id DESC");
        if (limit > 0) {
            full.limit(limit, 0);
        }
        SqlBuilder.Built built = full.build();
        return JdbcTemplate.query(built.getSql(), MAPPER, built.getParams());
    }

    @Override
    public List<FeedbackReport> listByUser(long userId) {
        // 命中 idx_fb_user
        String sql = "SELECT " + COLUMNS + FROM_JOIN
                + "WHERE f.user_id = ? ORDER BY f.created_at DESC, f.feedback_id DESC";
        return JdbcTemplate.query(sql, MAPPER, Long.valueOf(userId));
    }

    @Override
    public FeedbackReport findById(long feedbackId) {
        String sql = "SELECT " + COLUMNS + FROM_JOIN + "WHERE f.feedback_id = ?";
        return JdbcTemplate.queryOne(sql, MAPPER, Long.valueOf(feedbackId));
    }

    @Override
    public int countByStatus(String status) {
        // 命中 idx_fb_status
        Integer n = JdbcTemplate.queryInt(
                "SELECT COUNT(*) FROM feedback_reports WHERE status = ?", status);
        return n == null ? 0 : n.intValue();
    }

    @Override
    public int countByType(String type) {
        Integer n = JdbcTemplate.queryInt(
                "SELECT COUNT(*) FROM feedback_reports WHERE feedback_type = ?", type);
        return n == null ? 0 : n.intValue();
    }

    @Override
    public int countAll() {
        Integer n = JdbcTemplate.queryInt("SELECT COUNT(*) FROM feedback_reports");
        return n == null ? 0 : n.intValue();
    }

    @Override
    public long insert(FeedbackReport f) {
        Long id = JdbcTemplate.insert(INSERT_SQL,
                f.getUserId(), f.getFeedbackType(), f.getContent(), f.getContactInfo(),
                f.getStatus() == null ? STATUS_PENDING : f.getStatus());
        return id == null ? 0L : id.longValue();
    }

    @Override
    public int handle(long feedbackId, long handlerId, LocalDateTime handleTime) {
        // 一次性写状态 + 处理人 + 处理时间，保证"已处理"必定有处理痕迹
        return JdbcTemplate.update(
                "UPDATE feedback_reports SET status = ?, handler_id = ?, handle_time = ? "
                        + "WHERE feedback_id = ?",
                STATUS_PROCESSED, Long.valueOf(handlerId),
                handleTime == null ? com.zfz.typhoon.util.DateUtil.now() : handleTime,
                Long.valueOf(feedbackId));
    }

    @Override
    public int reopen(long feedbackId) {
        return JdbcTemplate.update(
                "UPDATE feedback_reports SET status = ?, handler_id = NULL, handle_time = NULL "
                        + "WHERE feedback_id = ?",
                STATUS_PENDING, Long.valueOf(feedbackId));
    }

    @Override
    public int deleteById(long feedbackId) {
        return JdbcTemplate.update(
                "DELETE FROM feedback_reports WHERE feedback_id = ?", Long.valueOf(feedbackId));
    }
}
