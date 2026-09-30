package com.zfz.typhoon.dao.impl;

import com.zfz.typhoon.dao.SyncLogDao;
import com.zfz.typhoon.model.SyncLog;
import com.zfz.typhoon.util.DateUtil;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据同步日志表 DAO 实现。
 *
 * @author 追风者项目组
 */
public class SyncLogDaoImpl implements SyncLogDao {

    /** 联表带出数据源名称与代码，避免上层再查一次数据源表 */
    private static final String COLUMNS =
            "l.log_id, l.source_id, l.sync_time, l.record_count, l.sync_status, "
                    + "l.error_message, l.duration_seconds, s.source_name, s.source_code";

    private static final RowMapper<SyncLog> MAPPER = new RowMapper<SyncLog>() {
        @Override
        public SyncLog map(ResultSet rs) throws SQLException {
            SyncLog l = new SyncLog();
            l.setLogId(RsUtil.getLong(rs, "log_id"));
            l.setSourceId(RsUtil.getLong(rs, "source_id"));
            l.setSyncTime(RsUtil.getDateTime(rs, "sync_time"));
            l.setRecordCount(RsUtil.getInt(rs, "record_count"));
            l.setSyncStatus(RsUtil.getString(rs, "sync_status"));
            l.setErrorMessage(RsUtil.getString(rs, "error_message"));
            l.setDurationSeconds(RsUtil.getInt(rs, "duration_seconds"));
            l.setSourceName(RsUtil.getString(rs, "source_name"));
            l.setSourceCode(RsUtil.getString(rs, "source_code"));
            return l;
        }
    };

    private static final String FROM_JOIN =
            " FROM sync_logs l JOIN data_sources s ON s.source_id = l.source_id ";

    private static final String INSERT_SQL =
            "INSERT INTO sync_logs (source_id, sync_time, record_count, sync_status, "
                    + "error_message, duration_seconds) VALUES (?, ?, ?, ?, ?, ?)";

    /* ------------------------------------------------------------------ */
    /*  列表                                                               */
    /* ------------------------------------------------------------------ */

    @Override
    public List<SyncLog> listRecent(int limit) {
        String sql = "SELECT " + COLUMNS + FROM_JOIN
                + "ORDER BY l.sync_time DESC, l.log_id DESC" + limitClause(limit);
        return JdbcTemplate.query(sql, MAPPER);
    }

    @Override
    public List<SyncLog> listBySource(long sourceId, int limit) {
        // 命中 idx_sync_source_time
        String sql = "SELECT " + COLUMNS + FROM_JOIN
                + "WHERE l.source_id = ? ORDER BY l.sync_time DESC, l.log_id DESC" + limitClause(limit);
        return JdbcTemplate.query(sql, MAPPER, Long.valueOf(sourceId));
    }

    @Override
    public List<SyncLog> listFailed(int limit) {
        // 命中 idx_sync_status
        String sql = "SELECT " + COLUMNS + FROM_JOIN
                + "WHERE l.sync_status = ? ORDER BY l.sync_time DESC, l.log_id DESC" + limitClause(limit);
        return JdbcTemplate.query(sql, MAPPER, STATUS_FAILED);
    }

    @Override
    public List<SyncLog> listByStatus(String status, int limit) {
        String sql = "SELECT " + COLUMNS + FROM_JOIN
                + "WHERE l.sync_status = ? ORDER BY l.sync_time DESC, l.log_id DESC" + limitClause(limit);
        return JdbcTemplate.query(sql, MAPPER, status);
    }

    /* ------------------------------------------------------------------ */
    /*  统计                                                               */
    /* ------------------------------------------------------------------ */

    @Override
    public int countBySource(long sourceId) {
        Integer n = JdbcTemplate.queryInt(
                "SELECT COUNT(*) FROM sync_logs WHERE source_id = ?", Long.valueOf(sourceId));
        return n == null ? 0 : n.intValue();
    }

    @Override
    public int countBySourceAndStatus(long sourceId, String status) {
        Integer n = JdbcTemplate.queryInt(
                "SELECT COUNT(*) FROM sync_logs WHERE source_id = ? AND sync_status = ?",
                Long.valueOf(sourceId), status);
        return n == null ? 0 : n.intValue();
    }

    @Override
    public double successRate(long sourceId) {
        // 一次查询同时取总数与成功数，减少往返
        String sql = "SELECT COUNT(*) AS total, "
                + "SUM(CASE WHEN sync_status = ? THEN 1 ELSE 0 END) AS ok "
                + "FROM sync_logs WHERE source_id = ?";
        // 无同步记录时返回 0 而不是 100：
        // 原实现在 total == 0 时返回 100，于是"一次都没跑过的数据源"在后台
        // 监控面板上显示成"成功率 100%"，看起来比真正稳定运行的源还健康 ——
        // 这是典型的静默误导。没有任何记录就意味着没有任何成功记录，应如实返回 0。
        final double[] result = new double[]{0d};
        JdbcTemplate.query(sql, new RowMapper<Object>() {
            @Override
            public Object map(ResultSet rs) throws SQLException {
                int total = rs.getInt("total");
                int ok = rs.getInt("ok");
                result[0] = total == 0 ? 0d : (100d * ok / total);
                return null;
            }
        }, STATUS_SUCCESS, Long.valueOf(sourceId));
        return result[0];
    }

    @Override
    public int sumRecordCountToday(long sourceId) {
        // 用范围条件代替 DATE(sync_time) = CURDATE()，让 idx_sync_source_time 完整生效
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Integer n = JdbcTemplate.queryInt(
                "SELECT SUM(record_count) FROM sync_logs WHERE source_id = ? AND sync_time >= ?",
                Long.valueOf(sourceId), todayStart);
        return n == null ? 0 : n.intValue();
    }

    @Override
    public double avgDurationSeconds(long sourceId) {
        Double d = JdbcTemplate.queryDouble(
                "SELECT AVG(duration_seconds) FROM sync_logs WHERE source_id = ? AND duration_seconds IS NOT NULL",
                Long.valueOf(sourceId));
        return d == null ? 0d : d.doubleValue();
    }

    @Override
    public LocalDateTime findLastSyncTime(long sourceId) {
        String v = JdbcTemplate.queryString(
                "SELECT MAX(sync_time) FROM sync_logs WHERE source_id = ?", Long.valueOf(sourceId));
        return v == null ? null : DateUtil.parseLenient(v);
    }

    @Override
    public int countByStatus(String status) {
        Integer n = JdbcTemplate.queryInt(
                "SELECT COUNT(*) FROM sync_logs WHERE sync_status = ?", status);
        return n == null ? 0 : n.intValue();
    }

    @Override
    public int countAll() {
        Integer n = JdbcTemplate.queryInt("SELECT COUNT(*) FROM sync_logs");
        return n == null ? 0 : n.intValue();
    }

    /* ------------------------------------------------------------------ */
    /*  写入                                                               */
    /* ------------------------------------------------------------------ */

    @Override
    public long insert(SyncLog l) {
        Long id = JdbcTemplate.insert(INSERT_SQL,
                l.getSourceId(),
                l.getSyncTime() == null ? DateUtil.now() : l.getSyncTime(),
                l.getRecordCount() == null ? Integer.valueOf(0) : l.getRecordCount(),
                l.getSyncStatus() == null ? STATUS_SUCCESS : l.getSyncStatus(),
                l.getErrorMessage(), l.getDurationSeconds());
        return id == null ? 0L : id.longValue();
    }

    @Override
    public int deleteBefore(LocalDateTime before) {
        return JdbcTemplate.update("DELETE FROM sync_logs WHERE sync_time < ?", before);
    }

    /* ------------------------------------------------------------------ */
    /*  内部实现                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * 生成 LIMIT 子句。
     *
     * <p>这里拼接的是<b>常量数字</b>（由代码传入并做过范围校验），
     * 不是用户原始输入，因此不构成注入风险；
     * 之所以不绑定 {@code ?}，是因为 MySQL 的
     * {@code PreparedStatement} 对 LIMIT 占位符在部分驱动版本上兼容性不佳。
     *
     * @param limit 条数，&lt;=0 表示不限
     * @return LIMIT 子句或空串
     */
    private static String limitClause(int limit) {
        if (limit <= 0) {
            return "";
        }
        int safe = Math.min(limit, 1000);
        return " LIMIT " + safe;
    }
}
