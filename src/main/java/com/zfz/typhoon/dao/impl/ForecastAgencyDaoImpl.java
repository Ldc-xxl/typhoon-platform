package com.zfz.typhoon.dao.impl;

import com.zfz.typhoon.dao.ForecastAgencyDao;
import com.zfz.typhoon.model.ForecastAgency;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 预报机构表 DAO 实现。
 *
 * @author 追风者项目组
 * @see com.zfz.typhoon.model.vo.AgencyVO
 */
public class ForecastAgencyDaoImpl implements ForecastAgencyDao {

    private static final String COLUMNS =
            "a.agency_id, a.agency_code, a.agency_name, a.region, a.display_color, "
                    + "a.official_url, a.description, a.status";

    private static final RowMapper<ForecastAgency> MAPPER = new RowMapper<ForecastAgency>() {
        @Override
        public ForecastAgency map(ResultSet rs) throws SQLException {
            ForecastAgency a = new ForecastAgency();
            a.setAgencyId(RsUtil.getLong(rs, "agency_id"));
            a.setAgencyCode(RsUtil.getString(rs, "agency_code"));
            a.setAgencyName(RsUtil.getString(rs, "agency_name"));
            a.setRegion(RsUtil.getString(rs, "region"));
            a.setDisplayColor(RsUtil.getString(rs, "display_color"));
            a.setOfficialUrl(RsUtil.getString(rs, "official_url"));
            a.setDescription(RsUtil.getString(rs, "description"));
            a.setStatus(RsUtil.getBoolean(rs, "status"));
            return a;
        }
    };

    @Override
    public ForecastAgency findById(long agencyId) {
        String sql = "SELECT " + COLUMNS + " FROM forecast_agencies a WHERE a.agency_id = ?";
        return JdbcTemplate.queryOne(sql, MAPPER, Long.valueOf(agencyId));
    }

    @Override
    public ForecastAgency findByCode(String agencyCode) {
        // 命中唯一键 uk_agencies_code
        String sql = "SELECT " + COLUMNS + " FROM forecast_agencies a WHERE a.agency_code = ?";
        return JdbcTemplate.queryOne(sql, MAPPER, agencyCode);
    }

    @Override
    public List<ForecastAgency> listAll() {
        return JdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM forecast_agencies a ORDER BY a.agency_id ASC", MAPPER);
    }

    @Override
    public List<ForecastAgency> listEnabled() {
        return JdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM forecast_agencies a WHERE a.status = 1 "
                        + "ORDER BY a.agency_id ASC", MAPPER);
    }

    @Override
    public List<ForecastAgency> listByCodes(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return new ArrayList<ForecastAgency>();
        }
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < codes.size(); i++) {
            if (i > 0) {
                in.append(", ");
            }
            in.append('?');
        }
        String sql = "SELECT " + COLUMNS + " FROM forecast_agencies a "
                + "WHERE a.agency_code IN (" + in + ") ORDER BY a.agency_id ASC";
        return JdbcTemplate.query(sql, MAPPER, codes.toArray());
    }

    @Override
    public long insert(ForecastAgency a) {
        String sql = "INSERT INTO forecast_agencies (agency_code, agency_name, region, "
                + "display_color, official_url, description, status) VALUES (?, ?, ?, ?, ?, ?, ?)";
        Long id = JdbcTemplate.insert(sql,
                a.getAgencyCode(), a.getAgencyName(), a.getRegion(),
                a.getDisplayColor() == null ? "#1E90FF" : a.getDisplayColor(),
                a.getOfficialUrl(), a.getDescription(),
                Boolean.valueOf(a.getStatus() == null || a.getStatus().booleanValue()));
        return id == null ? 0L : id.longValue();
    }

    @Override
    public int update(ForecastAgency a) {
        String sql = "UPDATE forecast_agencies SET agency_code = ?, agency_name = ?, region = ?, "
                + "display_color = ?, official_url = ?, description = ?, status = ? "
                + "WHERE agency_id = ?";
        return JdbcTemplate.update(sql,
                a.getAgencyCode(), a.getAgencyName(), a.getRegion(), a.getDisplayColor(),
                a.getOfficialUrl(), a.getDescription(),
                Boolean.valueOf(a.getStatus() == null || a.getStatus().booleanValue()),
                a.getAgencyId());
    }

    @Override
    public int setEnabled(long agencyId, boolean enabled) {
        return JdbcTemplate.update(
                "UPDATE forecast_agencies SET status = ? WHERE agency_id = ?",
                Boolean.valueOf(enabled), Long.valueOf(agencyId));
    }
}
