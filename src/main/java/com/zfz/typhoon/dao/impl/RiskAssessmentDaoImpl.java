package com.zfz.typhoon.dao.impl;

import com.zfz.typhoon.dao.RiskAssessmentDao;
import com.zfz.typhoon.model.RiskAssessment;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;
import com.zfz.typhoon.util.SqlBuilder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 风险评估表 DAO 实现。
 *
 * <p>{@link #listLatestByTyphoon} 用派生表一次取出"每个城市的最新评估"，
 * 这是本表最容易写错的地方：直接 {@code SELECT * FROM risk_assessments WHERE typhoon_id = ?}
 * 会把同一城市的历史评估一起返回，导致地图上同一城市出现多个风险色。
 *
 * <h3>obs_time 列的兼容策略</h3>
 * 列 {@code obs_time}（评估基准观测时刻）是后续增量加的（见
 * {@code db/migration_risk_obs_time.sql}）。旧库若没执行该迁移，
 * <b>硬写这条列会让整张表的读写全部报 Unknown column</b>——一个可选的
 * 增强字段不该造成这种后果。因此这里在首次访问时探测一次列是否存在并缓存，
 * 据此在"含该列 / 不含该列"两套 SQL 之间切换。代价是两处 SQL 常量，
 * 换来的是"忘了跑迁移也不会全站报错"。
 *
 * @author 追风者项目组
 */
public class RiskAssessmentDaoImpl implements RiskAssessmentDao {

    /** 基础列（不含 obs_time） */
    private static final String COLUMNS_BASE =
            "r.assessment_id, r.typhoon_id, r.city_id, r.user_id, r.assessment_time, "
                    + "r.distance_km, r.eta_time, r.affected_start_time, r.affected_end_time, "
                    + "r.risk_level, r.risk_score, r.suggestion, r.created_at";

    /** 含基准时刻列 */
    private static final String COLUMNS_WITH_OBS =
            "r.assessment_id, r.typhoon_id, r.city_id, r.user_id, r.assessment_time, r.obs_time, "
                    + "r.distance_km, r.eta_time, r.affected_start_time, r.affected_end_time, "
                    + "r.risk_level, r.risk_score, r.suggestion, r.created_at";

    /** obs_time 列是否存在；null 表示尚未探测 */
    private static volatile Boolean obsTimeColumn = null;

    private static final RowMapper<RiskAssessment> MAPPER = new RowMapper<RiskAssessment>() {
        @Override
        public RiskAssessment map(ResultSet rs) throws SQLException {
            RiskAssessment r = new RiskAssessment();
            r.setAssessmentId(RsUtil.getLong(rs, "assessment_id"));
            r.setTyphoonId(RsUtil.getLong(rs, "typhoon_id"));
            r.setCityId(RsUtil.getLong(rs, "city_id"));
            r.setUserId(RsUtil.getLong(rs, "user_id"));
            r.setAssessmentTime(RsUtil.getDateTime(rs, "assessment_time"));
            // 列不在结果集里时 getTimestamp 会抛 SQLException，故必须先问过探测结果
            if (hasObsTimeColumn()) {
                r.setObsTime(RsUtil.getDateTime(rs, "obs_time"));
            }
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

    private static final String INSERT_SQL_BASE =
            "INSERT INTO risk_assessments (typhoon_id, city_id, user_id, assessment_time, "
                    + "distance_km, eta_time, affected_start_time, affected_end_time, "
                    + "risk_level, risk_score, suggestion) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_SQL_WITH_OBS =
            "INSERT INTO risk_assessments (typhoon_id, city_id, user_id, assessment_time, obs_time, "
                    + "distance_km, eta_time, affected_start_time, affected_end_time, "
                    + "risk_level, risk_score, suggestion) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /**
     * 当前该用哪一套列清单。
     *
     * @return SQL 片段
     */
    private static String columns() {
        return hasObsTimeColumn() ? COLUMNS_WITH_OBS : COLUMNS_BASE;
    }

    /**
     * 当前该用哪一条 INSERT。
     *
     * @return SQL
     */
    private static String insertSql() {
        return hasObsTimeColumn() ? INSERT_SQL_WITH_OBS : INSERT_SQL_BASE;
    }

    /**
     * obs_time 列是否存在（首次访问探测一次，结果缓存）。
     *
     * @return 存在返回 true
     */
    private static boolean hasObsTimeColumn() {
        Boolean cached = obsTimeColumn;
        if (cached == null) {
            synchronized (RiskAssessmentDaoImpl.class) {
                cached = obsTimeColumn;
                if (cached == null) {
                    cached = Boolean.valueOf(probeObsTimeColumn());
                    obsTimeColumn = cached;
                }
            }
        }
        return cached.booleanValue();
    }

    /**
     * 通过 information_schema 探测列是否存在。
     *
     * <p>探测本身失败（权限不足、元数据查询被禁用等）时按"没有该列"处理：
     * 宁可少存一列基准时刻，也不能让整张表的读写全挂。
     *
     * @return 存在返回 true
     */
    private static boolean probeObsTimeColumn() {
        try {
            Integer n = JdbcTemplate.queryInt(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'risk_assessments' "
                            + "AND COLUMN_NAME = 'obs_time'");
            return n != null && n.intValue() > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public RiskAssessment findLatest(long typhoonId, long cityId) {
        String sql = "SELECT " + columns() + " FROM risk_assessments r "
                + "WHERE r.typhoon_id = ? AND r.city_id = ? "
                + "ORDER BY r.assessment_time DESC, r.assessment_id DESC LIMIT 1";
        return JdbcTemplate.queryOne(sql, MAPPER, Long.valueOf(typhoonId), Long.valueOf(cityId));
    }

    @Override
    public RiskAssessment findLatestByUser(long typhoonId, long cityId, long userId) {
        String sql = "SELECT " + columns() + " FROM risk_assessments r "
                + "WHERE r.typhoon_id = ? AND r.city_id = ? AND r.user_id = ? "
                + "ORDER BY r.assessment_time DESC, r.assessment_id DESC LIMIT 1";
        return JdbcTemplate.queryOne(sql, MAPPER,
                Long.valueOf(typhoonId), Long.valueOf(cityId), Long.valueOf(userId));
    }

    @Override
    public List<RiskAssessment> listLatestByTyphoon(long typhoonId) {
        // 派生表 m 求每个城市的最大 assessment_time，再回表取整行
        String sql = "SELECT " + columns() + " FROM risk_assessments r "
                + "JOIN (SELECT city_id, MAX(assessment_time) AS mt FROM risk_assessments "
                + "      WHERE typhoon_id = ? GROUP BY city_id) m "
                + "  ON m.city_id = r.city_id AND m.mt = r.assessment_time "
                + "WHERE r.typhoon_id = ? "
                + "ORDER BY r.risk_score DESC, r.city_id ASC";
        return JdbcTemplate.query(sql, MAPPER, Long.valueOf(typhoonId), Long.valueOf(typhoonId));
    }

    @Override
    public List<RiskAssessment> listByUser(long userId, int limit) {
        SqlBuilder sb = SqlBuilder.select(columns()).from("risk_assessments r")
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
        Long id = JdbcTemplate.insert(insertSql(), paramsOf(r));
        return id == null ? 0L : id.longValue();
    }

    @Override
    public int update(RiskAssessment r) {
        if (hasObsTimeColumn()) {
            String sql = "UPDATE risk_assessments SET obs_time = ?, distance_km = ?, eta_time = ?, "
                    + "affected_start_time = ?, affected_end_time = ?, risk_level = ?, "
                    + "risk_score = ?, suggestion = ? WHERE assessment_id = ?";
            return JdbcTemplate.update(sql, r.getObsTime(),
                    r.getDistanceKm(), r.getEtaTime(), r.getAffectedStartTime(), r.getAffectedEndTime(),
                    r.getRiskLevel(), r.getRiskScore(), r.getSuggestion(), r.getAssessmentId());
        }
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
     * 摊平参数（顺序与 {@link #insertSql()} 一致）。
     *
     * <p>用 List 逐项追加而不是直接写数组字面量：obs_time 是否参与取决于
     * 列探测结果，写死数组就没法在两种情况下共用。
     *
     * @param r 评估记录
     * @return 参数数组
     */
    private static Object[] paramsOf(RiskAssessment r) {
        List<Object> p = new ArrayList<Object>(12);
        p.add(r.getTyphoonId());
        p.add(r.getCityId());
        p.add(r.getUserId());
        p.add(r.getAssessmentTime() == null ? com.zfz.typhoon.util.DateUtil.now() : r.getAssessmentTime());
        if (hasObsTimeColumn()) {
            p.add(r.getObsTime());
        }
        p.add(r.getDistanceKm() == null ? Double.valueOf(0d) : r.getDistanceKm());
        p.add(r.getEtaTime());
        p.add(r.getAffectedStartTime());
        p.add(r.getAffectedEndTime());
        p.add(r.getRiskLevel());
        p.add(r.getRiskScore());
        p.add(r.getSuggestion());
        return p.toArray();
    }
}
