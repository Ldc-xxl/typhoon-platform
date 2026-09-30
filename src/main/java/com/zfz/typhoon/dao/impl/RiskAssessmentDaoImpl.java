package com.zfz.typhoon.dao.impl;

import com.zfz.typhoon.dao.RiskAssessmentDao;
import com.zfz.typhoon.model.RiskAssessment;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;
import com.zfz.typhoon.util.SqlBuilder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 风险评估表 DAO 实现。
 *
 * <p>{@link #listLatestByTyphoon} 用派生表一次取出"每个城市的最新评估"，
 * 这是本表最容易写错的地方：直接 {@code SELECT * FROM risk_assessments WHERE typhoon_id = ?}
 * 会把同一城市的历史评估一起返回，导致地图上同一城市出现多个风险色。
 *
 * @author 追风者项目组
 */
public class RiskAssessmentDaoImpl implements RiskAssessmentDao {

    private static final String COLUMNS =
            "r.assessment_id, r.typhoon_id, r.city_id, r.user_id, r.assessment_time, "
                    + "r.distance_km, r.eta_time, r.affected_start_time, r.affected_end_time, "
                    + "r.risk_level, r.risk_score, r.suggestion, r.created_at";

    private static final RowMapper<RiskAssessment> MAPPER = new RowMapper<RiskAssessment>() {
        @Override
        public RiskAssessment map(ResultSet rs) throws SQLException {
            RiskAssessment r = new RiskAssessment();
            r.setAssessmentId(RsUtil.getLong(rs, "assessment_id"));
            r.setTyphoonId(RsUtil.getLong(rs, "typhoon_id"));
            r.setCityId(RsUtil.getLong(rs, "city_id"));
            r.setUserId(RsUtil.getLong(rs, "user_id"));
            r.setAssessmentTime(RsUtil.getDateTime(rs, "assessment_time"));
            r.setDistanceKm(RsUtil.getDouble(rs, "distance_km"));
            r.setEtaTime(RsUtil.getDateTime(rs, "eta_time"));
            r.setAffectedStartTime(RsUtil.getDateTime(rs, "affected_start_time"));
            r.setAffectedEndTime(RsUtil.getDateTime(rs, "affected_end_time"));
            r.setRiskLevel(RsUtil.getString(rs, "risk_level"));
            r.setRiskScore(RsUtil.getDouble(rs, "risk_score"));
            r.setSuggestion(RsUtil.getString(rs, "suggestion"));
            r.setCreatedAt(RsUtil.getDateTime(rs, "created_at"));
            return r;
        }
    };

    private static final String INSERT_SQL =
            "INSERT INTO risk_assessments (typhoon_id, city_id, user_id, assessment_time, "
                    + "distance_km, eta_time, affected_start_time, affected_end_time, "
                    + "risk_level, risk_score, suggestion) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Override
    public RiskAssessment findLatest(long typhoonId, long cityId) {
        String sql = "SELECT " + COLUMNS + " FROM risk_assessments r "
                + "WHERE r.typhoon_id = ? AND r.city_id = ? "
                + "ORDER BY r.assessment_time DESC, r.assessment_id DESC LIMIT 1";
        return JdbcTemplate.queryOne(sql, MAPPER, Long.valueOf(typhoonId), Long.valueOf(cityId));
    }

    @Override
    public RiskAssessment findLatestByUser(long typhoonId, long cityId, long userId) {
        String sql = "SELECT " + COLUMNS + " FROM risk_assessments r "
                + "WHERE r.typhoon_id = ? AND r.city_id = ? AND r.user_id = ? "
                + "ORDER BY r.assessment_time DESC, r.assessment_id DESC LIMIT 1";
        return JdbcTemplate.queryOne(sql, MAPPER,
                Long.valueOf(typhoonId), Long.valueOf(cityId), Long.valueOf(userId));
    }

    @Override
    public List<RiskAssessment> listLatestByTyphoon(long typhoonId) {
        // 派生表 m 求每个城市的最大 assessment_time，再回表取整行
        String sql = "SELECT " + COLUMNS + " FROM risk_assessments r "
                + "JOIN (SELECT city_id, MAX(assessment_time) AS mt FROM risk_assessments "
                + "      WHERE typhoon_id = ? GROUP BY city_id) m "
                + "  ON m.city_id = r.city_id AND m.mt = r.assessment_time "
                + "WHERE r.typhoon_id = ? "
                + "ORDER BY r.risk_score DESC, r.city_id ASC";
        return JdbcTemplate.query(sql, MAPPER, Long.valueOf(typhoonId), Long.valueOf(typhoonId));
    }

    @Override
    public List<RiskAssessment> listByUser(long userId, int limit) {
        SqlBuilder sb = SqlBuilder.select(COLUMNS).from("risk_assessments r")
                .eq("r.user_id", Long.valueOf(userId))
                .orderBy("r.assessment_time DESC, r.assessment_id DESC");
        if (limit > 0) {
            sb.limit(limit, 0);
        }
        SqlBuilder.Built built = sb.build();
        return JdbcTemplate.query(built.getSql(), MAPPER, built.getParams());
    }

    @Override
    public List<Long> listAssessedCityIds(long typhoonId) {
        return JdbcTemplate.query(
                "SELECT DISTINCT city_id FROM risk_assessments WHERE typhoon_id = ? ORDER BY city_id",
                new RowMapper<Long>() {
                    @Override
                    public Long map(ResultSet rs) throws SQLException {
                        return RsUtil.getLong(rs, "city_id");
                    }
                }, Long.valueOf(typhoonId));
    }

    @Override
    public long insert(RiskAssessment r) {
        Long id = JdbcTemplate.insert(INSERT_SQL, paramsOf(r));
        return id == null ? 0L : id.longValue();
    }

    @Override
    public int update(RiskAssessment r) {
        String sql = "UPDATE risk_assessments SET distance_km = ?, eta_time = ?, "
                + "affected_start_time = ?, affected_end_time = ?, risk_level = ?, "
                + "risk_score = ?, suggestion = ? WHERE assessment_id = ?";
        return JdbcTemplate.update(sql,
                r.getDistanceKm(), r.getEtaTime(), r.getAffectedStartTime(), r.getAffectedEndTime(),
                r.getRiskLevel(), r.getRiskScore(), r.getSuggestion(), r.getAssessmentId());
    }

    @Override
    public int deleteByTyphoon(long typhoonId) {
        return JdbcTemplate.update(
                "DELETE FROM risk_assessments WHERE typhoon_id = ?", Long.valueOf(typhoonId));
    }

    /**
     * 摊平参数（顺序与 INSERT_SQL 一致）。
     *
     * @param r 评估记录
     * @return 参数数组
     */
    private static Object[] paramsOf(RiskAssessment r) {
        return new Object[]{
                r.getTyphoonId(), r.getCityId(), r.getUserId(),
                r.getAssessmentTime() == null ? com.zfz.typhoon.util.DateUtil.now() : r.getAssessmentTime(),
                r.getDistanceKm() == null ? Double.valueOf(0d) : r.getDistanceKm(),
                r.getEtaTime(), r.getAffectedStartTime(), r.getAffectedEndTime(),
                r.getRiskLevel(), r.getRiskScore(), r.getSuggestion()
        };
    }
}
