package com.zfz.typhoon.dao.impl;

import com.zfz.typhoon.dao.ForecastPathDao;
import com.zfz.typhoon.model.ForecastPath;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 预报路径表 DAO 实现。
 *
 * <p>本类是"多机构预报路径对比"与"预报分歧量化"的数据入口，
 * 关键设计是<b>用派生表一次取出各机构最新发布</b>，避免
 * 按机构循环查询造成的 N+1（3 家机构 × 每次对比 3 次查询）。
 *
 * @author 追风者项目组
 */
public class ForecastPathDaoImpl implements ForecastPathDao {

    /** 预报点列 + 三张机构表字段（联表后一次性取全，避免二次查询） */
    private static final String COLUMNS =
            "fp.forecast_point_id, fp.typhoon_id, fp.agency_id, fp.publish_time, fp.forecast_time, "
                    + "fp.latitude, fp.longitude, fp.forecast_wind_force, fp.forecast_wind_speed, "
                    + "fp.forecast_pressure, a.agency_code, a.agency_name, a.display_color";

    private static final RowMapper<ForecastPath> MAPPER = new RowMapper<ForecastPath>() {
        @Override
        public ForecastPath map(ResultSet rs) throws SQLException {
            ForecastPath p = new ForecastPath();
            p.setForecastPointId(RsUtil.getLong(rs, "forecast_point_id"));
            p.setTyphoonId(RsUtil.getLong(rs, "typhoon_id"));
            p.setAgencyId(RsUtil.getLong(rs, "agency_id"));
            p.setPublishTime(RsUtil.getDateTime(rs, "publish_time"));
            p.setForecastTime(RsUtil.getDateTime(rs, "forecast_time"));
            p.setLatitude(RsUtil.getDouble(rs, "latitude"));
            p.setLongitude(RsUtil.getDouble(rs, "longitude"));
            p.setForecastWindForce(RsUtil.getInt(rs, "forecast_wind_force"));
            p.setForecastWindSpeed(RsUtil.getInt(rs, "forecast_wind_speed"));
            p.setForecastPressure(RsUtil.getInt(rs, "forecast_pressure"));
            p.setAgencyCode(RsUtil.getString(rs, "agency_code"));
            p.setAgencyName(RsUtil.getString(rs, "agency_name"));
            p.setDisplayColor(RsUtil.getString(rs, "display_color"));
            return p;
        }
    };

    private static final String INSERT_SQL =
            "INSERT INTO forecast_paths (typhoon_id, agency_id, publish_time, forecast_time, "
                    + "latitude, longitude, forecast_wind_force, forecast_wind_speed, forecast_pressure) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /* ------------------------------------------------------------------ */
    /*  查询                                                               */
    /* ------------------------------------------------------------------ */

    @Override
    public List<ForecastPath> listByTyphoon(long typhoonId) {
        String sql = "SELECT " + COLUMNS + " FROM forecast_paths fp "
                + "JOIN forecast_agencies a ON a.agency_id = fp.agency_id "
                + "WHERE fp.typhoon_id = ? ORDER BY fp.agency_id ASC, fp.forecast_time ASC";
        return JdbcTemplate.query(sql, MAPPER, Long.valueOf(typhoonId));
    }

    @Override
    public List<ForecastPath> listLatestByTyphoon(long typhoonId) {
        // 派生表 m：每个机构在该台风下的最大 publish_time（走 idx_fp_typhoon_agency 前缀）
        // 再 JOIN 回主表只取这一批点。无论多少家机构，始终 1 次查询。
        String sql = "SELECT " + COLUMNS + " FROM forecast_paths fp "
                + "JOIN forecast_agencies a ON a.agency_id = fp.agency_id "
                + "JOIN (SELECT agency_id, MAX(publish_time) AS mp FROM forecast_paths "
                + "      WHERE typhoon_id = ? GROUP BY agency_id) m "
                + "  ON m.agency_id = fp.agency_id AND m.mp = fp.publish_time "
                + "WHERE fp.typhoon_id = ? "
                + "ORDER BY fp.agency_id ASC, fp.forecast_time ASC";
        return JdbcTemplate.query(sql, MAPPER, Long.valueOf(typhoonId), Long.valueOf(typhoonId));
    }

    @Override
    public List<ForecastPath> listLatestByTyphoonAndAgency(long typhoonId, String agencyCode) {
        String sql = "SELECT " + COLUMNS + " FROM forecast_paths fp "
                + "JOIN forecast_agencies a ON a.agency_id = fp.agency_id "
                + "WHERE fp.typhoon_id = ? AND a.agency_code = ? "
                + "  AND fp.publish_time = (SELECT MAX(fp2.publish_time) FROM forecast_paths fp2 "
                + "                         WHERE fp2.typhoon_id = fp.typhoon_id "
                + "                         AND fp2.agency_id = fp.agency_id) "
                + "ORDER BY fp.forecast_time ASC";
        return JdbcTemplate.query(sql, MAPPER, Long.valueOf(typhoonId), agencyCode);
    }

    @Override
    public LocalDateTime findLatestPublishTime(long typhoonId, String agencyCode) {
        String sql = "SELECT MAX(fp.publish_time) FROM forecast_paths fp "
                + "JOIN forecast_agencies a ON a.agency_id = fp.agency_id "
                + "WHERE fp.typhoon_id = ? AND a.agency_code = ?";
        String v = JdbcTemplate.queryString(sql, Long.valueOf(typhoonId), agencyCode);
        return v == null ? null : com.zfz.typhoon.util.DateUtil.parseLenient(v);
    }

    @Override
    public LocalDateTime findOldestLatestPublishTime(long typhoonId) {
        // 先按机构取 MAX，再对结果取 MIN —— "最旧的最近一次更新"
        String sql = "SELECT MIN(x.mp) FROM ("
                + "  SELECT fp.agency_id, MAX(fp.publish_time) AS mp FROM forecast_paths fp "
                + "  WHERE fp.typhoon_id = ? GROUP BY fp.agency_id) x";
        String v = JdbcTemplate.queryString(sql, Long.valueOf(typhoonId));
        return v == null ? null : com.zfz.typhoon.util.DateUtil.parseLenient(v);
    }

    @Override
    public int countByTyphoon(long typhoonId) {
        Integer n = JdbcTemplate.queryInt(
                "SELECT COUNT(*) FROM forecast_paths WHERE typhoon_id = ?", Long.valueOf(typhoonId));
        return n == null ? 0 : n.intValue();
    }

    /* ------------------------------------------------------------------ */
    /*  写入                                                               */
    /* ------------------------------------------------------------------ */

    @Override
    public long insert(ForecastPath p) {
        Long id = JdbcTemplate.insert(INSERT_SQL, paramsOf(p));
        return id == null ? 0L : id.longValue();
    }

    @Override
    public int batchInsert(List<ForecastPath> paths) {
        if (paths == null || paths.isEmpty()) {
            return 0;
        }
        List<Object[]> rows = new ArrayList<Object[]>(paths.size());
        for (ForecastPath p : paths) {
            rows.add(paramsOf(p));
        }
        return JdbcTemplate.batchUpdate(INSERT_SQL, rows);
    }

    @Override
    public int deleteByTyphoonAgencyAndPublish(long typhoonId, long agencyId, LocalDateTime publishTime) {
        return JdbcTemplate.update(
                "DELETE FROM forecast_paths WHERE typhoon_id = ? AND agency_id = ? AND publish_time = ?",
                Long.valueOf(typhoonId), Long.valueOf(agencyId), publishTime);
    }

    @Override
    public int deleteByTyphoon(long typhoonId) {
        return JdbcTemplate.update(
                "DELETE FROM forecast_paths WHERE typhoon_id = ?", Long.valueOf(typhoonId));
    }

    /* ------------------------------------------------------------------ */
    /*  内部实现                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * 把预报点摊平为参数数组（顺序与 INSERT_SQL 一致）。
     *
     * <p>注意：{@code agencyCode / agencyName / displayColor} 来自联表，
     * <b>不属于本表列</b>，故不参与写入。
     *
     * @param p 预报点
     * @return 参数数组
     */
    private static Object[] paramsOf(ForecastPath p) {
        return new Object[]{
                p.getTyphoonId(), p.getAgencyId(), p.getPublishTime(), p.getForecastTime(),
                p.getLatitude(), p.getLongitude(), p.getForecastWindForce(),
                p.getForecastWindSpeed(), p.getForecastPressure()
        };
    }
}
