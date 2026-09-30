package com.zfz.typhoon.dao.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.zfz.typhoon.dao.ForecastDisagreeRecordDao;
import com.zfz.typhoon.model.ForecastDisagreeRecord;
import com.zfz.typhoon.util.JsonUtil;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 预报分歧分析记录表 DAO 实现。
 *
 * <h3>JSON 列处理约定（本类与其它含 JSON 列的 DAO 一致）</h3>
 * <ul>
 *   <li><b>写入</b>：{@link JsonNode} 交给 {@link #toJsonText} 转成字符串，
 *       由 MySQL 的 JSON 列自行校验合法性——写非法 JSON 会直接报错，
 *       比应用层静默存坏数据更安全；</li>
 *   <li><b>读取</b>：用 {@link JsonUtil#parse} 反序列化回 {@link JsonNode}，
 *       解析失败时<b>不抛异常</b>而是返回 null，
 *       避免一条脏历史数据导致整个接口 500。</li>
 * </ul>
 *
 * @author 追风者项目组
 */
public class ForecastDisagreeRecordDaoImpl implements ForecastDisagreeRecordDao {

    private static final String COLUMNS =
            "r.record_id, r.typhoon_id, r.analysis_time, r.landing_point_deviation_km, "
                    + "r.landing_time_deviation_hours, r.peak_intensity_deviation, "
                    + "r.move_speed_deviation, r.variance_score, r.is_high_uncertainty, "
                    + "r.analysis_detail, r.created_at";

    private static final RowMapper<ForecastDisagreeRecord> MAPPER = new RowMapper<ForecastDisagreeRecord>() {
        @Override
        public ForecastDisagreeRecord map(ResultSet rs) throws SQLException {
            ForecastDisagreeRecord r = new ForecastDisagreeRecord();
            r.setRecordId(RsUtil.getLong(rs, "record_id"));
            r.setTyphoonId(RsUtil.getLong(rs, "typhoon_id"));
            r.setAnalysisTime(RsUtil.getDateTime(rs, "analysis_time"));
            r.setLandingPointDeviationKm(RsUtil.getDouble(rs, "landing_point_deviation_km"));
            r.setLandingTimeDeviationHours(RsUtil.getDouble(rs, "landing_time_deviation_hours"));
            r.setPeakIntensityDeviation(RsUtil.getDouble(rs, "peak_intensity_deviation"));
            r.setMoveSpeedDeviation(RsUtil.getDouble(rs, "move_speed_deviation"));
            r.setVarianceScore(RsUtil.getDouble(rs, "variance_score"));
            r.setHighUncertainty(RsUtil.getBoolean(rs, "is_high_uncertainty"));
            r.setAnalysisDetail(readJson(RsUtil.getString(rs, "analysis_detail")));
            r.setCreatedAt(RsUtil.getDateTime(rs, "created_at"));
            return r;
        }
    };

    private static final String INSERT_SQL =
            "INSERT INTO forecast_disagree_records (typhoon_id, analysis_time, "
                    + "landing_point_deviation_km, landing_time_deviation_hours, peak_intensity_deviation, "
                    + "move_speed_deviation, variance_score, is_high_uncertainty, analysis_detail) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /* ------------------------------------------------------------------ */
    /*  查询                                                               */
    /* ------------------------------------------------------------------ */

    @Override
    public ForecastDisagreeRecord findLatestByTyphoon(long typhoonId) {
        // 命中 idx_fdr_typhoon_time (typhoon_id, analysis_time)
        String sql = "SELECT " + COLUMNS + " FROM forecast_disagree_records r "
                + "WHERE r.typhoon_id = ? ORDER BY r.analysis_time DESC, r.record_id DESC LIMIT 1";
        return JdbcTemplate.queryOne(sql, MAPPER, Long.valueOf(typhoonId));
    }

    @Override
    public List<ForecastDisagreeRecord> listByTyphoon(long typhoonId) {
        String sql = "SELECT " + COLUMNS + " FROM forecast_disagree_records r "
                + "WHERE r.typhoon_id = ? ORDER BY r.analysis_time ASC, r.record_id ASC";
        return JdbcTemplate.query(sql, MAPPER, Long.valueOf(typhoonId));
    }

    @Override
    public ForecastDisagreeRecord findByTyphoonAndTime(long typhoonId, LocalDateTime analysisTime) {
        String sql = "SELECT " + COLUMNS + " FROM forecast_disagree_records r "
                + "WHERE r.typhoon_id = ? AND r.analysis_time = ?";
        return JdbcTemplate.queryOne(sql, MAPPER, Long.valueOf(typhoonId), analysisTime);
    }

    @Override
    public List<ForecastDisagreeRecord> listHighUncertainty() {
        // 命中 idx_fdr_uncertainty；按综合方差降序把最不确定的排前面
        String sql = "SELECT " + COLUMNS + " FROM forecast_disagree_records r "
                + "WHERE r.is_high_uncertainty = 1 "
                + "ORDER BY r.variance_score DESC, r.analysis_time DESC";
        return JdbcTemplate.query(sql, MAPPER);
    }

    /* ------------------------------------------------------------------ */
    /*  写入                                                               */
    /* ------------------------------------------------------------------ */

    @Override
    public long insert(ForecastDisagreeRecord r) {
        Long id = JdbcTemplate.insert(INSERT_SQL, paramsOf(r));
        return id == null ? 0L : id.longValue();
    }

    @Override
    public int update(ForecastDisagreeRecord r) {
        String sql = "UPDATE forecast_disagree_records SET landing_point_deviation_km = ?, "
                + "landing_time_deviation_hours = ?, peak_intensity_deviation = ?, "
                + "move_speed_deviation = ?, variance_score = ?, is_high_uncertainty = ?, "
                + "analysis_detail = ? WHERE record_id = ?";
        return JdbcTemplate.update(sql,
                r.getLandingPointDeviationKm(), r.getLandingTimeDeviationHours(),
                r.getPeakIntensityDeviation(), r.getMoveSpeedDeviation(), r.getVarianceScore(),
                Boolean.valueOf(r.getHighUncertainty() != null && r.getHighUncertainty().booleanValue()),
                toJsonText(r.getAnalysisDetail()), r.getRecordId());
    }

    @Override
    public int deleteByTyphoon(long typhoonId) {
        return JdbcTemplate.update(
                "DELETE FROM forecast_disagree_records WHERE typhoon_id = ?", Long.valueOf(typhoonId));
    }

    /* ------------------------------------------------------------------ */
    /*  JSON 辅助（供本包其它 DAO 复用同一套语义）                            */
    /* ------------------------------------------------------------------ */

    /**
     * JsonNode → JSON 文本；null 或空节点返回 null（写入 NULL 而非 'null'）。
     *
     * @param node JSON 节点
     * @return JSON 文本或 null
     */
    static String toJsonText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.toString();
    }

    /**
     * JSON 文本 → JsonNode；为空或格式非法时返回 null（不抛异常）。
     *
     * @param text JSON 文本
     * @return JSON 节点或 null
     */
    static JsonNode readJson(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return JsonUtil.parse(text, JsonNode.class);
        } catch (RuntimeException e) {
            // 历史脏数据不应让整个接口失败，记录后降级为 null
            System.err.println("[DAO] JSON 解析失败，已降级为 null：" + e.getMessage());
            return null;
        }
    }

    /**
     * 摊平参数（顺序与 INSERT_SQL 一致）。
     *
     * @param r 分歧记录
     * @return 参数数组
     */
    private static Object[] paramsOf(ForecastDisagreeRecord r) {
        return new Object[]{
                r.getTyphoonId(),
                r.getAnalysisTime() == null ? com.zfz.typhoon.util.DateUtil.now() : r.getAnalysisTime(),
                r.getLandingPointDeviationKm(), r.getLandingTimeDeviationHours(),
                r.getPeakIntensityDeviation(), r.getMoveSpeedDeviation(), r.getVarianceScore(),
                Boolean.valueOf(r.getHighUncertainty() != null && r.getHighUncertainty().booleanValue()),
                toJsonText(r.getAnalysisDetail())
        };
    }
}
