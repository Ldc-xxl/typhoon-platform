package com.zfz.typhoon.dao.impl;

import com.zfz.typhoon.dao.UserBehaviorSurveyDao;
import com.zfz.typhoon.model.SurveyAggResult;
import com.zfz.typhoon.model.UserBehaviorSurvey;
import com.zfz.typhoon.util.DateUtil;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户防灾行为调研表 DAO 实现。
 *
 * <h3>聚合 SQL 的关键点</h3>
 * 用 {@code SUM(CASE WHEN ... THEN 1 ELSE 0 END)} 而非多次子查询：
 * 一次全表扫描即可算出全部 7 个维度，避免 7 次 COUNT。分母统一用
 * {@code COUNT(*)}，并用 {@code NULLIF(COUNT(*), 0)} 防止除零。
 *
 * @author 追风者项目组
 */
public class UserBehaviorSurveyDaoImpl implements UserBehaviorSurveyDao {

    private static final String COLUMNS =
            "s.survey_id, s.typhoon_id, s.city_id, s.question_set_version, s.prepared_supplies, "
                    + "s.pay_attention_evacuation, s.worried_disaster_type, s.trust_level, "
                    + "s.submit_time, s.contact_info";

    private static final RowMapper<UserBehaviorSurvey> MAPPER = new RowMapper<UserBehaviorSurvey>() {
        @Override
        public UserBehaviorSurvey map(ResultSet rs) throws SQLException {
            UserBehaviorSurvey s = new UserBehaviorSurvey();
            s.setSurveyId(RsUtil.getLong(rs, "survey_id"));
            s.setTyphoonId(RsUtil.getLong(rs, "typhoon_id"));
            s.setCityId(RsUtil.getLong(rs, "city_id"));
            s.setQuestionSetVersion(RsUtil.getString(rs, "question_set_version"));
            s.setPreparedSupplies(RsUtil.getBoolean(rs, "prepared_supplies"));
            s.setPayAttentionEvacuation(RsUtil.getBoolean(rs, "pay_attention_evacuation"));
            s.setWorriedDisasterType(RsUtil.getString(rs, "worried_disaster_type"));
            s.setTrustLevel(RsUtil.getInt(rs, "trust_level"));
            s.setSubmitTime(RsUtil.getDateTime(rs, "submit_time"));
            s.setContactInfo(RsUtil.getString(rs, "contact_info"));
            return s;
        }
    };

    private static final String INSERT_SQL =
            "INSERT INTO user_behavior_surveys (typhoon_id, city_id, question_set_version, "
                    + "prepared_supplies, pay_attention_evacuation, worried_disaster_type, "
                    + "trust_level, contact_info) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    /**
     * 聚合计算列片段（7 个维度）。
     *
     * <p>全部以百分比形式返回，保留两位小数（{@code decimal(5,2)} 同精度）。
     * 使用 {@code 100.0 *} 强制浮点运算，避免整数除法被截断为 0。
     */
    private static final String AGG_EXPRESSIONS =
            "s.city_id AS city_id, "
                    + "COUNT(*) AS total_count, "
                    + "ROUND(100.0 * SUM(CASE WHEN s.prepared_supplies = 1 THEN 1 ELSE 0 END) "
                    + "      / NULLIF(COUNT(*), 0), 2) AS supply_prep_rate, "
                    + "ROUND(100.0 * SUM(CASE WHEN s.pay_attention_evacuation = 1 THEN 1 ELSE 0 END) "
                    + "      / NULLIF(COUNT(*), 0), 2) AS evacuation_attention_rate, "
                    + "ROUND(100.0 * SUM(CASE WHEN s.worried_disaster_type = 'RAIN' THEN 1 ELSE 0 END) "
                    + "      / NULLIF(COUNT(*), 0), 2) AS heavy_rain_worry_rate, "
                    + "ROUND(100.0 * SUM(CASE WHEN s.worried_disaster_type = 'STORM_SURGE' THEN 1 ELSE 0 END) "
                    + "      / NULLIF(COUNT(*), 0), 2) AS storm_surge_worry_rate, "
                    + "ROUND(100.0 * SUM(CASE WHEN s.trust_level >= 4 THEN 1 ELSE 0 END) "
                    + "      / NULLIF(COUNT(*), 0), 2) AS high_trust_rate, "
                    + "ROUND(100.0 * SUM(CASE WHEN s.trust_level <= 2 THEN 1 ELSE 0 END) "
                    + "      / NULLIF(COUNT(*), 0), 2) AS risk_overestimation_rate, "
                    + "ROUND(100.0 * SUM(CASE WHEN s.trust_level = 5 THEN 1 ELSE 0 END) "
                    + "      / NULLIF(COUNT(*), 0), 2) AS risk_underestimation_rate";

    /** 聚合结果映射器（只填统计字段，城市名由 Service 用城市缓存补齐） */
    private static final RowMapper<SurveyAggResult> AGG_MAPPER = new RowMapper<SurveyAggResult>() {
        @Override
        public SurveyAggResult map(ResultSet rs) throws SQLException {
            SurveyAggResult a = new SurveyAggResult();
            a.setCityId(RsUtil.getLong(rs, "city_id"));
            a.setTotalCount(RsUtil.getInt(rs, "total_count"));
            a.setSupplyPrepRate(RsUtil.getDouble(rs, "supply_prep_rate"));
            a.setEvacuationAttentionRate(RsUtil.getDouble(rs, "evacuation_attention_rate"));
            a.setHeavyRainWorryRate(RsUtil.getDouble(rs, "heavy_rain_worry_rate"));
            a.setStormSurgeWorryRate(RsUtil.getDouble(rs, "storm_surge_worry_rate"));
            a.setHighTrustRate(RsUtil.getDouble(rs, "high_trust_rate"));
            a.setRiskOverestimationRate(RsUtil.getDouble(rs, "risk_overestimation_rate"));
            a.setRiskUnderestimationRate(RsUtil.getDouble(rs, "risk_underestimation_rate"));
            return a;
        }
    };

    /* ------------------------------------------------------------------ */
    /*  写入                                                               */
    /* ------------------------------------------------------------------ */

    @Override
    public long insert(UserBehaviorSurvey s) {
        String sql = INSERT_SQL;
        Long id = JdbcTemplate.insert(sql,
                s.getTyphoonId(), s.getCityId(),
                s.getQuestionSetVersion() == null ? "V1.0" : s.getQuestionSetVersion(),
                Boolean.valueOf(s.getPreparedSupplies() != null && s.getPreparedSupplies().booleanValue()),
                Boolean.valueOf(s.getPayAttentionEvacuation() != null
                        && s.getPayAttentionEvacuation().booleanValue()),
                s.getWorriedDisasterType() == null ? UserBehaviorSurvey.CONCERN_OTHER
                        : s.getWorriedDisasterType(),
                s.getTrustLevel(), s.getContactInfo());
        return id == null ? 0L : id.longValue();
    }

    /* ------------------------------------------------------------------ */
    /*  查询与统计                                                          */
    /* ------------------------------------------------------------------ */

    @Override
    public List<UserBehaviorSurvey> listByTyphoonAndCity(long typhoonId, long cityId) {
        String sql = "SELECT " + COLUMNS + " FROM user_behavior_surveys s "
                + "WHERE s.typhoon_id = ? AND s.city_id = ? ORDER BY s.submit_time ASC";
        return JdbcTemplate.query(sql, MAPPER, Long.valueOf(typhoonId), Long.valueOf(cityId));
    }

    @Override
    public int countByTyphoonAndCity(long typhoonId, long cityId) {
        Integer n = JdbcTemplate.queryInt(
                "SELECT COUNT(*) FROM user_behavior_surveys WHERE typhoon_id = ? AND city_id = ?",
                Long.valueOf(typhoonId), Long.valueOf(cityId));
        return n == null ? 0 : n.intValue();
    }

    @Override
    public int countByTyphoon(long typhoonId) {
        Integer n = JdbcTemplate.queryInt(
                "SELECT COUNT(*) FROM user_behavior_surveys WHERE typhoon_id = ?",
                Long.valueOf(typhoonId));
        return n == null ? 0 : n.intValue();
    }

    @Override
    public int countAll() {
        Integer n = JdbcTemplate.queryInt("SELECT COUNT(*) FROM user_behavior_surveys");
        return n == null ? 0 : n.intValue();
    }

    @Override
    public List<SurveyAggResult> aggregateByTyphoon(long typhoonId) {
        // 命中 idx_ubs_typhoon；GROUP BY city_id 后按样本量降序，样本大的城市结论更稳
        String sql = "SELECT " + AGG_EXPRESSIONS + " FROM user_behavior_surveys s "
                + "WHERE s.typhoon_id = ? GROUP BY s.city_id ORDER BY total_count DESC";
        return JdbcTemplate.query(sql, AGG_MAPPER, Long.valueOf(typhoonId));
    }

    @Override
    public SurveyAggResult aggregateByTyphoonAndCity(long typhoonId, long cityId) {
        String sql = "SELECT " + AGG_EXPRESSIONS + " FROM user_behavior_surveys s "
                + "WHERE s.typhoon_id = ? AND s.city_id = ? GROUP BY s.city_id";
        return JdbcTemplate.queryOne(sql, AGG_MAPPER, Long.valueOf(typhoonId), Long.valueOf(cityId));
    }

    @Override
    public LocalDateTime[] submitTimeRange(long typhoonId) {
        LocalDateTime[] range = new LocalDateTime[]{null, null};
        String sql = "SELECT MIN(s.submit_time) AS t_min, MAX(s.submit_time) AS t_max "
                + "FROM user_behavior_surveys s WHERE s.typhoon_id = ?";
        final LocalDateTime[] holder = range;
        JdbcTemplate.query(sql, new RowMapper<Object>() {
            @Override
            public Object map(ResultSet rs) throws SQLException {
                holder[0] = RsUtil.getDateTime(rs, "t_min");
                holder[1] = RsUtil.getDateTime(rs, "t_max");
                return null;
            }
        }, Long.valueOf(typhoonId));
        return range;
    }

    @Override
    public int deleteByTyphoon(long typhoonId) {
        return JdbcTemplate.update(
                "DELETE FROM user_behavior_surveys WHERE typhoon_id = ?", Long.valueOf(typhoonId));
    }

    /**
     * 脱敏联系方式并写入（供 Service 使用的便捷入口，避免上层忘记脱敏）。
     *
     * @param survey        问卷
     * @param maskedContact 已脱敏的联系方式
     * @return 新生成的主键
     */
    public long insertMasked(UserBehaviorSurvey survey, String maskedContact) {
        survey.setContactInfo(maskedContact);
        return insert(survey);
    }

    /**
     * 计算"提交距今小时数"，用于 Service 判断样本是否新鲜。
     *
     * @param typhoonId 台风ID
     * @return 最近一份问卷距今小时数；无样本返回 -1
     */
    public double latestSubmitAgeHours(long typhoonId) {
        LocalDateTime[] range = submitTimeRange(typhoonId);
        if (range[1] == null) {
            return -1d;
        }
        return DateUtil.hoursBetween(range[1], DateUtil.now());
    }
}
