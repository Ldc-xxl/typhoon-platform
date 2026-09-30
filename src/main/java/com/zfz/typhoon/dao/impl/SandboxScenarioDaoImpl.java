package com.zfz.typhoon.dao.impl;

import com.zfz.typhoon.dao.SandboxScenarioDao;
import com.zfz.typhoon.model.SandboxScenario;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;
import com.zfz.typhoon.util.SqlBuilder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 沙盘模拟情景表 DAO 实现。
 *
 * @author 追风者项目组
 */
public class SandboxScenarioDaoImpl implements SandboxScenarioDao {

    private static final String COLUMNS =
            "s.scenario_id, s.user_id, s.base_typhoon_id, s.scenario_name, s.scenario_type, "
                    + "s.params_json, s.result_json, s.is_public, s.created_at, s.updated_at";

    private static final RowMapper<SandboxScenario> MAPPER = new RowMapper<SandboxScenario>() {
        @Override
        public SandboxScenario map(ResultSet rs) throws SQLException {
            SandboxScenario s = new SandboxScenario();
            s.setScenarioId(RsUtil.getLong(rs, "scenario_id"));
            s.setUserId(RsUtil.getLong(rs, "user_id"));
            s.setBaseTyphoonId(RsUtil.getLong(rs, "base_typhoon_id"));
            s.setScenarioName(RsUtil.getString(rs, "scenario_name"));
            s.setScenarioType(RsUtil.getString(rs, "scenario_type"));
            s.setParamsJson(ForecastDisagreeRecordDaoImpl.readJson(RsUtil.getString(rs, "params_json")));
            s.setResultJson(ForecastDisagreeRecordDaoImpl.readJson(RsUtil.getString(rs, "result_json")));
            s.setPublicShared(RsUtil.getBoolean(rs, "is_public"));
            s.setCreatedAt(RsUtil.getDateTime(rs, "created_at"));
            s.setUpdatedAt(RsUtil.getDateTime(rs, "updated_at"));
            return s;
        }
    };

    private static final String INSERT_SQL =
            "INSERT INTO sandbox_scenarios (user_id, base_typhoon_id, scenario_name, scenario_type, "
                    + "params_json, result_json, is_public) VALUES (?, ?, ?, ?, ?, ?, ?)";

    @Override
    public SandboxScenario findById(long scenarioId) {
        String sql = "SELECT " + COLUMNS + " FROM sandbox_scenarios s WHERE s.scenario_id = ?";
        return JdbcTemplate.queryOne(sql, MAPPER, Long.valueOf(scenarioId));
    }

    @Override
    public List<SandboxScenario> listByUser(long userId) {
        // 命中 idx_ss_user
        String sql = "SELECT " + COLUMNS + " FROM sandbox_scenarios s "
                + "WHERE s.user_id = ? ORDER BY s.updated_at DESC, s.scenario_id DESC";
        return JdbcTemplate.query(sql, MAPPER, Long.valueOf(userId));
    }

    @Override
    public List<SandboxScenario> listByUserAndTyphoon(long userId, long baseTyphoonId) {
        SqlBuilder sb = SqlBuilder.select(COLUMNS).from("sandbox_scenarios s")
                .eq("s.user_id", Long.valueOf(userId))
                .eq("s.base_typhoon_id", Long.valueOf(baseTyphoonId))
                .orderBy("s.updated_at DESC, s.scenario_id DESC");
        SqlBuilder.Built built = sb.build();
        return JdbcTemplate.query(built.getSql(), MAPPER, built.getParams());
    }

    @Override
    public List<SandboxScenario> listPublic(int limit) {
        SqlBuilder sb = SqlBuilder.select(COLUMNS).from("sandbox_scenarios s")
                .eq("s.is_public", Integer.valueOf(1))
                .orderBy("s.updated_at DESC, s.scenario_id DESC");
        if (limit > 0) {
            sb.limit(limit, 0);
        }
        SqlBuilder.Built built = sb.build();
        return JdbcTemplate.query(built.getSql(), MAPPER, built.getParams());
    }

    @Override
    public List<SandboxScenario> listByBaseTyphoon(long baseTyphoonId) {
        // 命中 idx_ss_base_typhoon
        String sql = "SELECT " + COLUMNS + " FROM sandbox_scenarios s "
                + "WHERE s.base_typhoon_id = ? ORDER BY s.updated_at DESC, s.scenario_id DESC";
        return JdbcTemplate.query(sql, MAPPER, Long.valueOf(baseTyphoonId));
    }

    @Override
    public int countByUser(long userId) {
        Integer n = JdbcTemplate.queryInt(
                "SELECT COUNT(*) FROM sandbox_scenarios WHERE user_id = ?", Long.valueOf(userId));
        return n == null ? 0 : n.intValue();
    }

    @Override
    public int countAll() {
        Integer n = JdbcTemplate.queryInt("SELECT COUNT(*) FROM sandbox_scenarios");
        return n == null ? 0 : n.intValue();
    }

    @Override
    public long insert(SandboxScenario s) {
        Long id = JdbcTemplate.insert(INSERT_SQL,
                s.getUserId(), s.getBaseTyphoonId(), s.getScenarioName(),
                s.getScenarioType() == null ? SandboxScenario.TYPE_BASELINE : s.getScenarioType(),
                ForecastDisagreeRecordDaoImpl.toJsonText(s.getParamsJson()),
                ForecastDisagreeRecordDaoImpl.toJsonText(s.getResultJson()),
                Boolean.valueOf(s.getPublicShared() != null && s.getPublicShared().booleanValue()));
        return id == null ? 0L : id.longValue();
    }

    @Override
    public int update(SandboxScenario s) {
        String sql = "UPDATE sandbox_scenarios SET scenario_name = ?, scenario_type = ?, "
                + "params_json = ?, result_json = ?, is_public = ? WHERE scenario_id = ?";
        return JdbcTemplate.update(sql,
                s.getScenarioName(), s.getScenarioType(),
                ForecastDisagreeRecordDaoImpl.toJsonText(s.getParamsJson()),
                ForecastDisagreeRecordDaoImpl.toJsonText(s.getResultJson()),
                Boolean.valueOf(s.getPublicShared() != null && s.getPublicShared().booleanValue()),
                s.getScenarioId());
    }

    @Override
    public int setPublic(long scenarioId, boolean shared) {
        return JdbcTemplate.update(
                "UPDATE sandbox_scenarios SET is_public = ? WHERE scenario_id = ?",
                Boolean.valueOf(shared), Long.valueOf(scenarioId));
    }

    @Override
    public int deleteById(long scenarioId) {
        return JdbcTemplate.update(
                "DELETE FROM sandbox_scenarios WHERE scenario_id = ?", Long.valueOf(scenarioId));
    }
}
