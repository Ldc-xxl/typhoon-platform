package com.zfz.typhoon.dao.impl;

import com.zfz.typhoon.dao.AiPredictionRecordDao;
import com.zfz.typhoon.model.AiPredictionRecord;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 大模型智能测算记录表 DAO 实现。
 *
 * @author 追风者项目组
 */
public class AiPredictionRecordDaoImpl implements AiPredictionRecordDao {

    private static final String COLUMNS =
            "a.record_id, a.typhoon_id, a.model_name, a.prompt_version, a.prediction_time, "
                    + "a.predicted_path, a.predicted_landfall_lat, a.predicted_landfall_lon, "
                    + "a.confidence_score, a.deviation_vs_agencies, a.latency_ms, a.summary_text, "
                    + "a.status, a.created_at";

    private static final RowMapper<AiPredictionRecord> MAPPER = new RowMapper<AiPredictionRecord>() {
        @Override
        public AiPredictionRecord map(ResultSet rs) throws SQLException {
            AiPredictionRecord a = new AiPredictionRecord();
            a.setRecordId(RsUtil.getLong(rs, "record_id"));
            a.setTyphoonId(RsUtil.getLong(rs, "typhoon_id"));
            a.setModelName(RsUtil.getString(rs, "model_name"));
            a.setPromptVersion(RsUtil.getString(rs, "prompt_version"));
            a.setPredictionTime(RsUtil.getDateTime(rs, "prediction_time"));
            a.setPredictedPath(ForecastDisagreeRecordDaoImpl.readJson(RsUtil.getString(rs, "predicted_path")));
            a.setPredictedLandfallLat(RsUtil.getDouble(rs, "predicted_landfall_lat"));
            a.setPredictedLandfallLon(RsUtil.getDouble(rs, "predicted_landfall_lon"));
            a.setConfidenceScore(RsUtil.getDouble(rs, "confidence_score"));
            a.setDeviationVsAgencies(RsUtil.getDouble(rs, "deviation_vs_agencies"));
            a.setLatencyMs(RsUtil.getInt(rs, "latency_ms"));
            a.setSummaryText(RsUtil.getString(rs, "summary_text"));
            a.setStatus(RsUtil.getString(rs, "status"));
            a.setCreatedAt(RsUtil.getDateTime(rs, "created_at"));
            return a;
        }
    };

    private static final String INSERT_SQL =
            "INSERT INTO ai_prediction_records (typhoon_id, model_name, prompt_version, "
                    + "prediction_time, predicted_path, predicted_landfall_lat, predicted_landfall_lon, "
                    + "confidence_score, deviation_vs_agencies, latency_ms, summary_text, status) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /* ------------------------------------------------------------------ */
    /*  查询                                                               */
    /* ------------------------------------------------------------------ */

    @Override
    public List<AiPredictionRecord> listLatestPerModel(long typhoonId) {
        // 派生表 m：每个 model_name 的最大 prediction_time（命中 idx_ai_model / idx_ai_typhoon_time）
        String sql = "SELECT " + COLUMNS + " FROM ai_prediction_records a "
                + "JOIN (SELECT model_name, MAX(prediction_time) AS mt FROM ai_prediction_records "
                + "      WHERE typhoon_id = ? GROUP BY model_name) m "
                + "  ON m.model_name = a.model_name AND m.mt = a.prediction_time "
                + "WHERE a.typhoon_id = ? "
                + "ORDER BY a.model_name ASC";
        return JdbcTemplate.query(sql, MAPPER, Long.valueOf(typhoonId), Long.valueOf(typhoonId));
    }

    @Override
    public AiPredictionRecord findLatest(long typhoonId) {
        // 命中 idx_ai_typhoon_time (typhoon_id, prediction_time)
        String sql = "SELECT " + COLUMNS + " FROM ai_prediction_records a "
                + "WHERE a.typhoon_id = ? ORDER BY a.prediction_time DESC, a.record_id DESC LIMIT 1";
        return JdbcTemplate.queryOne(sql, MAPPER, Long.valueOf(typhoonId));
    }

    @Override
    public List<AiPredictionRecord> listByTyphoon(long typhoonId) {
        String sql = "SELECT " + COLUMNS + " FROM ai_prediction_records a "
                + "WHERE a.typhoon_id = ? ORDER BY a.prediction_time DESC, a.record_id DESC";
        return JdbcTemplate.query(sql, MAPPER, Long.valueOf(typhoonId));
    }

    @Override
    public List<AiPredictionRecord> listByTyphoonAndModel(long typhoonId, String modelName) {
        String sql = "SELECT " + COLUMNS + " FROM ai_prediction_records a "
                + "WHERE a.typhoon_id = ? AND a.model_name = ? "
                + "ORDER BY a.prediction_time DESC, a.record_id DESC";
        return JdbcTemplate.query(sql, MAPPER, Long.valueOf(typhoonId), modelName);
    }

    @Override
    public int countByTyphoon(long typhoonId) {
        Integer n = JdbcTemplate.queryInt(
                "SELECT COUNT(*) FROM ai_prediction_records WHERE typhoon_id = ?",
                Long.valueOf(typhoonId));
        return n == null ? 0 : n.intValue();
    }

    @Override
    public List<Object[]> countGroupByModel() {
        // 返回的每行是两个 Object：[模型名, 次数]，供管理端表格直接渲染
        return JdbcTemplate.query(
                "SELECT model_name, COUNT(*) AS cnt FROM ai_prediction_records "
                        + "GROUP BY model_name ORDER BY cnt DESC",
                new RowMapper<Object[]>() {
                    @Override
                    public Object[] map(ResultSet rs) throws SQLException {
                        return new Object[]{
                                RsUtil.getString(rs, "model_name"),
                                RsUtil.getLong(rs, "cnt")
                        };
                    }
                });
    }

    @Override
    public List<Object[]> statsGroupByModel() {
        // 一条 GROUP BY 同时算出四项指标：管理端表格一次查询渲染完成。
        // AVG(confidence_score) 在无记录时返回 NULL，由 Service 层转成 "—"，
        // 这里不做 COALESCE，以免把"没有数据"伪装成"置信度为 0"。
        return JdbcTemplate.query(
                "SELECT model_name, COUNT(*) AS cnt, AVG(confidence_score) AS avg_conf, "
                        + "MAX(prediction_time) AS last_time FROM ai_prediction_records "
                        + "GROUP BY model_name ORDER BY cnt DESC",
                new RowMapper<Object[]>() {
                    @Override
                    public Object[] map(ResultSet rs) throws SQLException {
                        return new Object[]{
                                RsUtil.getString(rs, "model_name"),
                                RsUtil.getLong(rs, "cnt"),
                                RsUtil.getDouble(rs, "avg_conf"),
                                RsUtil.getDateTime(rs, "last_time")                        };
                    }
                });
    }

    @Override
    public java.util.Map<String, Double> avgLatencyByModel() {
        // 只统计 latency_ms 非空的记录：融合算法的记录该列为 NULL，不该被算成 0ms。
        final java.util.Map<String, Double> result = new java.util.LinkedHashMap<String, Double>();
        JdbcTemplate.query(
                "SELECT model_name, AVG(latency_ms) AS avg_ms FROM ai_prediction_records "
                        + "WHERE latency_ms IS NOT NULL GROUP BY model_name",
                new RowMapper<Object[]>() {
                    @Override
                    public Object[] map(ResultSet rs) throws SQLException {
                        String name = RsUtil.getString(rs, "model_name");
                        Double avg = RsUtil.getDouble(rs, "avg_ms");
                        if (name != null && avg != null) {
                            result.put(name, avg);
                        }
                        return new Object[]{name, avg};
                    }
                });
        return result;
    }

    @Override
    public LocalDateTime findLatestPredictionTime(long typhoonId) {
        String v = JdbcTemplate.queryString(
                "SELECT MAX(prediction_time) FROM ai_prediction_records WHERE typhoon_id = ?",
                Long.valueOf(typhoonId));
        return v == null ? null : com.zfz.typhoon.util.DateUtil.parseLenient(v);
    }

    /* ------------------------------------------------------------------ */
    /*  写入                                                               */
    /* ------------------------------------------------------------------ */

    @Override
    public long insert(AiPredictionRecord a) {
        Long id = JdbcTemplate.insert(INSERT_SQL, paramsOf(a));
        return id == null ? 0L : id.longValue();
    }

    @Override
    public int updateStatus(long recordId, String status, Double deviationVsAgencies) {
        return JdbcTemplate.update(
                "UPDATE ai_prediction_records SET status = ?, deviation_vs_agencies = ? "
                        + "WHERE record_id = ?",
                status, deviationVsAgencies, Long.valueOf(recordId));
    }

    @Override
    public int deleteByTyphoon(long typhoonId) {
        return JdbcTemplate.update(
                "DELETE FROM ai_prediction_records WHERE typhoon_id = ?", Long.valueOf(typhoonId));
    }

    /**
     * 摊平参数（顺序与 INSERT_SQL 一致）。
     *
     * @param a 测算记录
     * @return 参数数组
     */
    private static Object[] paramsOf(AiPredictionRecord a) {
        return new Object[]{
                a.getTyphoonId(), a.getModelName(), a.getPromptVersion(),
                a.getPredictionTime() == null ? com.zfz.typhoon.util.DateUtil.now() : a.getPredictionTime(),
                ForecastDisagreeRecordDaoImpl.toJsonText(a.getPredictedPath()),
                a.getPredictedLandfallLat(), a.getPredictedLandfallLon(),
                a.getConfidenceScore(), a.getDeviationVsAgencies(), a.getLatencyMs(), a.getSummaryText(),
                a.getStatus() == null ? "PENDING" : a.getStatus()
        };
    }
}
