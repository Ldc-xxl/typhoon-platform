package com.zfz.typhoon.dao.impl;

import com.zfz.typhoon.dao.DisasterReportDao;
import com.zfz.typhoon.model.DisasterReport;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 灾情报告表 DAO 实现。
 *
 * <p>查询一律 {@code LEFT JOIN cities}：{@code main_city_id} 允许为 NULL
 * （见 DDL 注释"可为空"），用 LEFT JOIN 才不会把"无主要城市的灾情报告"丢掉。
 *
 * @author 追风者项目组
 */
public class DisasterReportDaoImpl implements DisasterReportDao {

    private static final String COLUMNS =
            "d.report_id, d.typhoon_id, d.affected_provinces, d.main_city_id, "
                    + "d.direct_economic_loss, d.casualties, d.injured, d.affected_population, "
                    + "d.disaster_summary, d.report_source, d.report_time, c.city_name AS main_city_name";

    private static final RowMapper<DisasterReport> MAPPER = new RowMapper<DisasterReport>() {
        @Override
        public DisasterReport map(ResultSet rs) throws SQLException {
            DisasterReport d = new DisasterReport();
            d.setReportId(RsUtil.getLong(rs, "report_id"));
            d.setTyphoonId(RsUtil.getLong(rs, "typhoon_id"));
            d.setAffectedProvinces(RsUtil.getString(rs, "affected_provinces"));
            d.setMainCityId(RsUtil.getLong(rs, "main_city_id"));
            d.setDirectEconomicLoss(RsUtil.getDouble(rs, "direct_economic_loss"));
            d.setCasualties(RsUtil.getInt(rs, "casualties"));
            d.setInjured(RsUtil.getInt(rs, "injured"));
            d.setAffectedPopulation(RsUtil.getInt(rs, "affected_population"));
            d.setDisasterSummary(RsUtil.getString(rs, "disaster_summary"));
            d.setReportSource(RsUtil.getString(rs, "report_source"));
            d.setReportTime(RsUtil.getDateTime(rs, "report_time"));
            d.setMainCityName(RsUtil.getString(rs, "main_city_name"));
            return d;
        }
    };

    private static final String FROM_JOIN =
            " FROM disaster_reports d LEFT JOIN cities c ON c.city_id = d.main_city_id ";

    private static final String INSERT_SQL =
            "INSERT INTO disaster_reports (typhoon_id, affected_provinces, main_city_id, "
                    + "direct_economic_loss, casualties, injured, affected_population, "
                    + "disaster_summary, report_source, report_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Override
    public List<DisasterReport> listByTyphoon(long typhoonId) {
        String sql = "SELECT " + COLUMNS + FROM_JOIN
                + "WHERE d.typhoon_id = ? ORDER BY d.report_time DESC, d.report_id DESC";
        return JdbcTemplate.query(sql, MAPPER, Long.valueOf(typhoonId));
    }

    @Override
    public DisasterReport findLatestByTyphoon(long typhoonId) {
        String sql = "SELECT " + COLUMNS + FROM_JOIN
                + "WHERE d.typhoon_id = ? ORDER BY d.report_time DESC, d.report_id DESC LIMIT 1";
        return JdbcTemplate.queryOne(sql, MAPPER, Long.valueOf(typhoonId));
    }

    @Override
    public DisasterReport findById(long reportId) {
        String sql = "SELECT " + COLUMNS + FROM_JOIN + "WHERE d.report_id = ?";
        return JdbcTemplate.queryOne(sql, MAPPER, Long.valueOf(reportId));
    }

    @Override
    public List<Long> listTyphoonIdsWithReport() {
        // DISTINCT 在 idx_dr_typhoon 上可完成松散索引扫描
        return JdbcTemplate.query(
                "SELECT DISTINCT typhoon_id FROM disaster_reports ORDER BY typhoon_id",
                new RowMapper<Long>() {
                    @Override
                    public Long map(ResultSet rs) throws SQLException {
                        return RsUtil.getLong(rs, "typhoon_id");
                    }
                });
    }

    @Override
    public long insert(DisasterReport d) {
        Long id = JdbcTemplate.insert(INSERT_SQL, paramsOf(d));
        return id == null ? 0L : id.longValue();
    }

    @Override
    public int update(DisasterReport d) {
        String sql = "UPDATE disaster_reports SET affected_provinces = ?, main_city_id = ?, "
                + "direct_economic_loss = ?, casualties = ?, injured = ?, affected_population = ?, "
                + "disaster_summary = ?, report_source = ?, report_time = ? WHERE report_id = ?";
        return JdbcTemplate.update(sql,
                d.getAffectedProvinces(), d.getMainCityId(), d.getDirectEconomicLoss(),
                d.getCasualties(), d.getInjured(), d.getAffectedPopulation(),
                d.getDisasterSummary(), d.getReportSource(), d.getReportTime(), d.getReportId());
    }

    @Override
    public int deleteByTyphoon(long typhoonId) {
        return JdbcTemplate.update(
                "DELETE FROM disaster_reports WHERE typhoon_id = ?", Long.valueOf(typhoonId));
    }

    /**
     * 摊平参数（顺序与 INSERT_SQL 一致）。
     *
     * @param d 灾情报告
     * @return 参数数组
     */
    private static Object[] paramsOf(DisasterReport d) {
        return new Object[]{
                d.getTyphoonId(), d.getAffectedProvinces(), d.getMainCityId(),
                d.getDirectEconomicLoss(), d.getCasualties(), d.getInjured(),
                d.getAffectedPopulation(), d.getDisasterSummary(), d.getReportSource(),
                d.getReportTime()
        };
    }
}
