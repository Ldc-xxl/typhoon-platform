package com.zfz.typhoon.dao.impl;

import com.zfz.typhoon.dao.SurveyAggResultDao;
import com.zfz.typhoon.model.SurveyAggResult;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;
import com.zfz.typhoon.util.SqlBuilder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 调研聚合统计表 DAO 实现。
 *
 * @author 追风者项目组
 */
public class SurveyAggResultDaoImpl implements SurveyAggResultDao {

    /** 联表取城市名与省份，避免 Service 再查一次城市表 */
    private static final String COLUMNS =
            "a.agg_id, a.typhoon_id, a.city_id, a.total_count, a.supply_prep_rate, "
                    + "a.evacuation_attention_rate, a.heavy_rain_worry_rate, a.storm_surge_worry_rate, "
                    + "a.high_trust_rate, a.risk_overestimation_rate, a.risk_underestimation_rate, "
                    + "a.stat_time, c.city_name, c.province";

    private static final RowMapper<SurveyAggResult> MAPPER = new RowMapper<SurveyAggResult>() {
        @Override
        public SurveyAggResult map(ResultSet rs) throws SQLException {
            SurveyAggResult a = new SurveyAggResult();
            a.setAggId(RsUtil.getLong(rs, "agg_id"));
            a.setTyphoonId(RsUtil.getLong(rs, "typhoon_id"));
            a.setCityId(RsUtil.getLong(rs, "city_id"));
            a.setTotalCount(RsUtil.getInt(rs, "total_count"));
            a.setSupplyPrepRate(RsUtil.getDouble(rs, "supply_prep_rate"));
            a.setEvacuationAttentionRate(RsUtil.getDouble(rs, "evacuation_attention_rate"));
            a.setHeavyRainWorryRate(RsUtil.getDouble(rs, "heavy_rain_worry_rate"));
            a.setStormSurgeWorryRate(RsUtil.getDouble(rs, "storm_surge_worry_rate"));
            a.setHighTrustRate(RsUtil.getDouble(rs, "high_trust_rate"));
            a.setRiskOverestimationRate(RsUtil.getDouble(rs, "risk_overestimation_rate"));
            a.setRiskUnderestimationRate(RsUtil.getDouble(rs, "risk_underestimation_rate"));
            a.setStatTime(RsUtil.getDateTime(rs, "stat_time"));
            a.setCityName(RsUtil.getString(rs, "city_name"));
            a.setProvince(RsUtil.getString(rs, "province"));
            return a;
        }
    };

    private static final String FROM_JOIN =
            " FROM survey_agg_results a LEFT JOIN cities c ON c.city_id = a.city_id ";

    private static final String INSERT_COLUMNS =
            "(typhoon_id, city_id, total_count, supply_prep_rate, evacuation_attention_rate, "
                    + "heavy_rain_worry_rate, storm_surge_worry_rate, high_trust_rate, "
                    + "risk_overestimation_rate, risk_underestimation_rate, stat_time) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_SQL = "INSERT INTO survey_agg_results " + INSERT_COLUMNS;

    private static final String UPSERT_SQL = "INSERT INTO survey_agg_results " + INSERT_COLUMNS
            + " ON DUPLICATE KEY UPDATE "
            + "total_count = VALUES(total_count), "
            + "supply_prep_rate = VALUES(supply_prep_rate), "
            + "evacuation_attention_rate = VALUES(evacuation_attention_rate), "
            + "heavy_rain_worry_rate = VALUES(heavy_rain_worry_rate), "
            + "storm_surge_worry_rate = VALUES(storm_surge_worry_rate), "
            + "high_trust_rate = VALUES(high_trust_rate), "
            + "risk_overestimation_rate = VALUES(risk_overestimation_rate), "
            + "risk_underestimation_rate = VALUES(risk_underestimation_rate), "
            + "stat_time = VALUES(stat_time)";

    @Override
    public List<SurveyAggResult> listByTyphoon(long typhoonId) {
        // 命中 uk_sar_typhoon_city 首列；样本量降序让结论更稳的城市排前面
        String sql = "SELECT " + COLUMNS + FROM_JOIN
                + "WHERE a.typhoon_id = ? ORDER BY a.total_count DESC, a.city_id ASC";
        return JdbcTemplate.query(sql, MAPPER, Long.valueOf(typhoonId));
    }

    @Override
    public SurveyAggResult findByTyphoonAndCity(long typhoonId, long cityId) {
        String sql = "SELECT " + COLUMNS + FROM_JOIN
                + "WHERE a.typhoon_id = ? AND a.city_id = ?";
        return JdbcTemplate.queryOne(sql, MAPPER, Long.valueOf(typhoonId), Long.valueOf(cityId));
    }

    @Override
    public List<SurveyAggResult> listTopBySampleSize(long typhoonId, int topN) {
        if (topN <= 0) {
            topN = 5;
        }
        String sql = "SELECT " + COLUMNS + FROM_JOIN
                + "WHERE a.typhoon_id = ? ORDER BY a.total_count DESC LIMIT ?";
        return JdbcTemplate.query(sql, MAPPER, Long.valueOf(typhoonId), Integer.valueOf(topN));
    }

    @Override
    public int sumTotalCount(long typhoonId) {
        Integer n = JdbcTemplate.queryInt(
                "SELECT SUM(total_count) FROM survey_agg_results WHERE typhoon_id = ?",
                Long.valueOf(typhoonId));
        return n == null ? 0 : n.intValue();
    }

    @Override
    public long insert(SurveyAggResult r) {
        Long id = JdbcTemplate.insert(INSERT_SQL, paramsOf(r));
        return id == null ? 0L : id.longValue();
    }

    @Override
    public int upsert(SurveyAggResult r) {
        return JdbcTemplate.update(UPSERT_SQL, paramsOf(r));
    }

    @Override
    public int upsertAll(List<SurveyAggResult> results) {
        if (results == null || results.isEmpty()) {
            return 0;
        }
        List<Object[]> rows = new ArrayList<Object[]>(results.size());
        for (SurveyAggResult r : results) {
            rows.add(paramsOf(r));
        }
        return JdbcTemplate.batchUpdate(UPSERT_SQL, rows);
    }

    @Override
    public int deleteByTyphoon(long typhoonId) {
        return JdbcTemplate.update(
                "DELETE FROM survey_agg_results WHERE typhoon_id = ?", Long.valueOf(typhoonId));
    }

    /**
     * 构造带条件的查询（保留扩展点）。
     *
     * @param typhoonId 台风ID
     * @return SQL 构造器
     */
    static SqlBuilder baseQuery(long typhoonId) {
        return SqlBuilder.select(COLUMNS).from("survey_agg_results a")
                .eq("a.typhoon_id", Long.valueOf(typhoonId));
    }

    /**
     * 摊平参数；比率缺失按 0 处理（列定义为 NOT NULL），
     * 统计时间缺失兜底为当前时间。
     *
     * @param r 聚合结果
     * @return 参数数组
     */
    private static Object[] paramsOf(SurveyAggResult r) {
        return new Object[]{
                r.getTyphoonId(), r.getCityId(),
                r.getTotalCount() == null ? Integer.valueOf(0) : r.getTotalCount(),
                nz(r.getSupplyPrepRate()), nz(r.getEvacuationAttentionRate()),
                nz(r.getHeavyRainWorryRate()), nz(r.getStormSurgeWorryRate()),
                nz(r.getHighTrustRate()), nz(r.getRiskOverestimationRate()),
                nz(r.getRiskUnderestimationRate()),
                r.getStatTime() == null ? com.zfz.typhoon.util.DateUtil.now() : r.getStatTime()
        };
    }

    /**
     * null → 0.0。
     *
     * @param v 比率
     * @return 非空比率
     */
    private static Double nz(Double v) {
        return v == null ? Double.valueOf(0d) : v;
    }
}
