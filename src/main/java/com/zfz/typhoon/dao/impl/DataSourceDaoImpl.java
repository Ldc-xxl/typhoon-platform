package com.zfz.typhoon.dao.impl;

import com.zfz.typhoon.dao.DataSourceDao;
import com.zfz.typhoon.model.DataSource;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;
import com.zfz.typhoon.util.SqlBuilder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据源表 DAO 实现。
 *
 * <p>注意：本表<b>不含</b> success_count / fail_count / avg_duration 列，
 * 映射器中也不设置实体的这三个字段（由 Service 用 {@code SyncLogDao} 的聚合计填充）。
 *
 * @author 追风者项目组
 */
public class DataSourceDaoImpl implements DataSourceDao {

    private static final String COLUMNS =
            "d.source_id, d.source_code, d.source_name, d.api_url, d.update_frequency, "
                    + "d.enabled, d.last_sync_time, d.description";

    private static final RowMapper<DataSource> MAPPER = new RowMapper<DataSource>() {
        @Override
        public DataSource map(ResultSet rs) throws SQLException {
            DataSource d = new DataSource();
            d.setSourceId(RsUtil.getLong(rs, "source_id"));
            d.setSourceCode(RsUtil.getString(rs, "source_code"));
            d.setSourceName(RsUtil.getString(rs, "source_name"));
            d.setApiUrl(RsUtil.getString(rs, "api_url"));
            d.setUpdateFrequency(RsUtil.getInt(rs, "update_frequency"));
            d.setEnabled(RsUtil.getBoolean(rs, "enabled"));
            d.setLastSyncTime(RsUtil.getDateTime(rs, "last_sync_time"));
            d.setDescription(RsUtil.getString(rs, "description"));
            // successCount / failCount / avgDuration 来自 sync_logs 聚合，不在此处赋值
            return d;
        }
    };

    private static final String INSERT_SQL =
            "INSERT INTO data_sources (source_code, source_name, api_url, update_frequency, "
                    + "enabled, last_sync_time, description) VALUES (?, ?, ?, ?, ?, ?, ?)";

    @Override
    public DataSource findById(long sourceId) {
        String sql = "SELECT " + COLUMNS + " FROM data_sources d WHERE d.source_id = ?";
        return JdbcTemplate.queryOne(sql, MAPPER, Long.valueOf(sourceId));
    }

    @Override
    public DataSource findByCode(String sourceCode) {
        // 命中唯一键 uk_sources_code
        String sql = "SELECT " + COLUMNS + " FROM data_sources d WHERE d.source_code = ?";
        return JdbcTemplate.queryOne(sql, MAPPER, sourceCode);
    }

    @Override
    public List<DataSource> listAll() {
        return JdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM data_sources d ORDER BY d.source_id ASC", MAPPER);
    }

    @Override
    public List<DataSource> listEnabled() {
        // 命中 idx_sources_enabled
        return JdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM data_sources d WHERE d.enabled = 1 "
                        + "ORDER BY d.source_id ASC", MAPPER);
    }

    @Override
    public int countEnabled() {
        Integer n = JdbcTemplate.queryInt("SELECT COUNT(*) FROM data_sources WHERE enabled = 1");
        return n == null ? 0 : n.intValue();
    }

    @Override
    public long insert(DataSource d) {
        Long id = JdbcTemplate.insert(INSERT_SQL,
                d.getSourceCode(), d.getSourceName(), d.getApiUrl(),
                d.getUpdateFrequency() == null ? Integer.valueOf(30) : d.getUpdateFrequency(),
                Boolean.valueOf(d.getEnabled() == null || d.getEnabled().booleanValue()),
                d.getLastSyncTime(), d.getDescription());
        return id == null ? 0L : id.longValue();
    }

    @Override
    public int update(DataSource d) {
        String sql = "UPDATE data_sources SET source_code = ?, source_name = ?, api_url = ?, "
                + "update_frequency = ?, enabled = ?, description = ? WHERE source_id = ?";
        return JdbcTemplate.update(sql,
                d.getSourceCode(), d.getSourceName(), d.getApiUrl(), d.getUpdateFrequency(),
                Boolean.valueOf(d.getEnabled() == null || d.getEnabled().booleanValue()),
                d.getDescription(), d.getSourceId());
    }

    @Override
    public int setEnabled(long sourceId, boolean enabled) {
        return JdbcTemplate.update(
                "UPDATE data_sources SET enabled = ? WHERE source_id = ?",
                Boolean.valueOf(enabled), Long.valueOf(sourceId));
    }

    @Override
    public int updateLastSyncTime(long sourceId, LocalDateTime syncTime) {
        return JdbcTemplate.update(
                "UPDATE data_sources SET last_sync_time = ? WHERE source_id = ?",
                syncTime, Long.valueOf(sourceId));
    }

    @Override
    public int deleteById(long sourceId) {
        return JdbcTemplate.update(
                "DELETE FROM data_sources WHERE source_id = ?", Long.valueOf(sourceId));
    }

    /**
     * 构造带条件的查询（保留扩展点，供后续"按接口地址模糊检索"使用）。
     *
     * @param keyword 关键词
     * @return SQL 构造器
     */
    static SqlBuilder searchQuery(String keyword) {
        return SqlBuilder.select(COLUMNS).from("data_sources d")
                .like("d.source_name", keyword)
                .orderBy("d.source_id ASC");
    }
}
