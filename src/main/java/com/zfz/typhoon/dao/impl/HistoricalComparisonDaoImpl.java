package com.zfz.typhoon.dao.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.zfz.typhoon.dao.HistoricalComparisonDao;
import com.zfz.typhoon.model.HistoricalComparison;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;
import com.zfz.typhoon.util.SqlBuilder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 历史对比任务表 DAO 实现。
 *
 * @author 追风者项目组
 */
public class HistoricalComparisonDaoImpl implements HistoricalComparisonDao {

    private static final String COLUMNS =
            "h.comparison_id, h.user_id, h.comparison_name, h.primary_typhoon_id, "
                    + "h.compare_typhoon_ids, h.result_summary, h.created_at";

    private static final RowMapper<HistoricalComparison> MAPPER = new RowMapper<HistoricalComparison>() {
        @Override
        public HistoricalComparison map(ResultSet rs) throws SQLException {
            HistoricalComparison h = new HistoricalComparison();
            h.setComparisonId(RsUtil.getLong(rs, "comparison_id"));
            h.setUserId(RsUtil.getLong(rs, "user_id"));
            h.setComparisonName(RsUtil.getString(rs, "comparison_name"));
            h.setPrimaryTyphoonId(RsUtil.getLong(rs, "primary_typhoon_id"));
            h.setCompareTyphoonIds(ForecastDisagreeRecordDaoImpl.readJson(
                    RsUtil.getString(rs, "compare_typhoon_ids")));
            h.setResultSummary(ForecastDisagreeRecordDaoImpl.readJson(
                    RsUtil.getString(rs, "result_summary")));
            h.setCreatedAt(RsUtil.getDateTime(rs, "created_at"));
            return h;
        }
    };

    private static final String INSERT_SQL =
            "INSERT INTO historical_comparisons (user_id, comparison_name, primary_typhoon_id, "
                    + "compare_typhoon_ids, result_summary) VALUES (?, ?, ?, ?, ?)";

    @Override
    public HistoricalComparison findById(long comparisonId) {
        String sql = "SELECT " + COLUMNS + " FROM historical_comparisons h WHERE h.comparison_id = ?";
        return JdbcTemplate.queryOne(sql, MAPPER, Long.valueOf(comparisonId));
    }

    @Override
    public List<HistoricalComparison> listByUser(long userId) {
        // 命中 idx_hc_user
        return JdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM historical_comparisons h WHERE h.user_id = ? "
                        + "ORDER BY h.created_at DESC, h.comparison_id DESC",
                MAPPER, Long.valueOf(userId));
    }

    @Override
    public List<HistoricalComparison> listByPrimaryTyphoon(long primaryTyphoonId) {
        // 命中 idx_hc_primary_typhoon
        return JdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM historical_comparisons h "
                        + "WHERE h.primary_typhoon_id = ? ORDER BY h.created_at DESC",
                MAPPER, Long.valueOf(primaryTyphoonId));
    }

    @Override
    public int countByUser(long userId) {
        Integer n = JdbcTemplate.queryInt(
                "SELECT COUNT(*) FROM historical_comparisons WHERE user_id = ?", Long.valueOf(userId));
        return n == null ? 0 : n.intValue();
    }

    @Override
    public long insert(HistoricalComparison h) {
        // compare_typhoon_ids 为 NOT NULL 的 JSON 列，缺失时兜底为空数组
        String ids = h.getCompareTyphoonIds() == null
                ? "[]" : ForecastDisagreeRecordDaoImpl.toJsonText(h.getCompareTyphoonIds());
        if (ids == null) {
            ids = "[]";
        }
        Long id = JdbcTemplate.insert(INSERT_SQL,
                h.getUserId(), h.getComparisonName(), h.getPrimaryTyphoonId(),
                ids, ForecastDisagreeRecordDaoImpl.toJsonText(h.getResultSummary()));
        return id == null ? 0L : id.longValue();
    }

    @Override
    public int updateResult(long comparisonId, String resultSummary) {
        return JdbcTemplate.update(
                "UPDATE historical_comparisons SET result_summary = ? WHERE comparison_id = ?",
                resultSummary, Long.valueOf(comparisonId));
    }

    @Override
    public int rename(long comparisonId, String comparisonName, long userId) {
        return JdbcTemplate.update(
                "UPDATE historical_comparisons SET comparison_name = ? "
                        + "WHERE comparison_id = ? AND user_id = ?",
                comparisonName, Long.valueOf(comparisonId), Long.valueOf(userId));
    }

    @Override
    public int deleteByIdAndUser(long comparisonId, long userId) {
        return JdbcTemplate.update(
                "DELETE FROM historical_comparisons WHERE comparison_id = ? AND user_id = ?",
                Long.valueOf(comparisonId), Long.valueOf(userId));
    }

    /**
     * 构造带条件的查询（保留扩展点，供按名称搜索对比任务使用）。
     *
     * @param userId  用户ID
     * @param keyword 名称关键词
     * @return SQL 构造器
     */
    static SqlBuilder searchQuery(long userId, String keyword) {
        return SqlBuilder.select(COLUMNS).from("historical_comparisons h")
                .eq("h.user_id", Long.valueOf(userId))
                .like("h.comparison_name", keyword)
                .orderBy("h.created_at DESC");
    }

    /**
     * 从 JSON 节点中读取对比台风ID数组（供 Service 校验参数合法性）。
     *
     * @param node JSON 节点
     * @return ID 列表；无数据返回空列表
     */
    public static java.util.List<Long> readIdArray(JsonNode node) {
        java.util.List<Long> ids = new java.util.ArrayList<Long>();
        if (node == null || !node.isArray()) {
            return ids;
        }
        for (JsonNode n : node) {
            if (n.isNumber()) {
                ids.add(Long.valueOf(n.asLong()));
            }
        }
        return ids;
    }
}
