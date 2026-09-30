package com.zfz.typhoon.dao.impl;

import com.zfz.typhoon.dao.CityImpactRecordDao;
import com.zfz.typhoon.model.CityImpactRecord;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;
import com.zfz.typhoon.util.SqlBuilder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 城市影响记录表 DAO 实现。
 *
 * <p>课程要求（阶段五 ·「批量入库」）：{@link #deleteByTyphoon} 与
 * {@link #batchInsert} 需在同一个 Service 事务内调用，
 * 否则"删除成功、插入失败"会让详情页显示空的城市列表。
 *
 * @author 追风者项目组
 */
public class CityImpactRecordDaoImpl implements CityImpactRecordDao {

    private static final String COLUMNS =
            "r.impact_id, r.typhoon_id, r.city_id, r.wind_circle_level, r.estimated_wind_force, "
                    + "r.impact_start_time, r.impact_end_time, r.distance_km, r.generated_at";

    private static final RowMapper<CityImpactRecord> MAPPER = new RowMapper<CityImpactRecord>() {
        @Override
        public CityImpactRecord map(ResultSet rs) throws SQLException {
            CityImpactRecord r = new CityImpactRecord();
            r.setImpactId(RsUtil.getLong(rs, "impact_id"));
            r.setTyphoonId(RsUtil.getLong(rs, "typhoon_id"));
            r.setCityId(RsUtil.getLong(rs, "city_id"));
            r.setWindCircleLevel(RsUtil.getInt(rs, "wind_circle_level"));
            r.setEstimatedWindForce(RsUtil.getInt(rs, "estimated_wind_force"));
            r.setImpactStartTime(RsUtil.getDateTime(rs, "impact_start_time"));
            r.setImpactEndTime(RsUtil.getDateTime(rs, "impact_end_time"));
            r.setDistanceKm(RsUtil.getDouble(rs, "distance_km"));
            r.setGeneratedAt(RsUtil.getDateTime(rs, "generated_at"));
            return r;
        }
    };

    private static final String INSERT_COLUMNS =
            "(typhoon_id, city_id, wind_circle_level, estimated_wind_force, "
                    + "impact_start_time, impact_end_time, distance_km, generated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_SQL = "INSERT INTO city_impact_records " + INSERT_COLUMNS;

    private static final String UPSERT_SQL = "INSERT INTO city_impact_records " + INSERT_COLUMNS
            + " ON DUPLICATE KEY UPDATE "
            + "estimated_wind_force = VALUES(estimated_wind_force), "
            + "impact_start_time = VALUES(impact_start_time), "
            + "impact_end_time = VALUES(impact_end_time), "
            + "distance_km = VALUES(distance_km)";

    /* ------------------------------------------------------------------ */
    /*  查询                                                               */
    /* ------------------------------------------------------------------ */

    @Override
    public List<CityImpactRecord> listByTyphoon(long typhoonId) {
        // 排序：先看风圈等级（影响强度），同等级先近后远
        String sql = "SELECT " + COLUMNS + " FROM city_impact_records r "
                + "WHERE r.typhoon_id = ? "
                + "ORDER BY r.wind_circle_level DESC, r.distance_km ASC";
        return JdbcTemplate.query(sql, MAPPER, Long.valueOf(typhoonId));
    }

    @Override
    public List<CityImpactRecord> listByTyphoonAndCity(long typhoonId, long cityId) {
        // 命中 idx_impact_city（city_id）与唯一键前两列
        String sql = "SELECT " + COLUMNS + " FROM city_impact_records r "
                + "WHERE r.typhoon_id = ? AND r.city_id = ? "
                + "ORDER BY r.wind_circle_level DESC, r.generated_at DESC";
        return JdbcTemplate.query(sql, MAPPER, Long.valueOf(typhoonId), Long.valueOf(cityId));
    }

    @Override
    public List<CityImpactRecord> listByWindCircleLevel(long typhoonId, int windCircleLevel) {
        String sql = "SELECT " + COLUMNS + " FROM city_impact_records r "
                + "WHERE r.typhoon_id = ? AND r.wind_circle_level = ? "
                + "ORDER BY r.distance_km ASC";
        return JdbcTemplate.query(sql, MAPPER,
                Long.valueOf(typhoonId), Integer.valueOf(windCircleLevel));
    }

    @Override
    public int countByTyphoon(long typhoonId) {
        Integer n = JdbcTemplate.queryInt(
                "SELECT COUNT(*) FROM city_impact_records WHERE typhoon_id = ?",
                Long.valueOf(typhoonId));
        return n == null ? 0 : n.intValue();
    }

    @Override
    public LocalDateTime findLatestGeneratedAt(long typhoonId) {
        // 复用 idx_impact_time 无法直接排序，故用 MAX 子查询；
        // 单台风影响记录量级在百条以内，代价可忽略。
        String sql = "SELECT MAX(r.generated_at) FROM city_impact_records r WHERE r.typhoon_id = ?";
        String v = JdbcTemplate.queryString(sql, Long.valueOf(typhoonId));
        return v == null ? null : com.zfz.typhoon.util.DateUtil.parseLenient(v);
    }

    /* ------------------------------------------------------------------ */
    /*  写入                                                               */
    /* ------------------------------------------------------------------ */

    @Override
    public int batchInsert(List<CityImpactRecord> records) {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        List<Object[]> rows = new ArrayList<Object[]>(records.size());
        for (CityImpactRecord r : records) {
            rows.add(paramsOf(r));
        }
        return JdbcTemplate.batchUpdate(INSERT_SQL, rows);
    }

    @Override
    public int upsertAll(List<CityImpactRecord> records) {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        List<Object[]> rows = new ArrayList<Object[]>(records.size());
        for (CityImpactRecord r : records) {
            rows.add(paramsOf(r));
        }
        return JdbcTemplate.batchUpdate(UPSERT_SQL, rows);
    }

    @Override
    public int deleteByTyphoon(long typhoonId) {
        return JdbcTemplate.update(
                "DELETE FROM city_impact_records WHERE typhoon_id = ?", Long.valueOf(typhoonId));
    }

    /* ------------------------------------------------------------------ */
    /*  内部实现                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * 摊平参数；{@code generated_at} 为空时由数据库默认值填充会破坏唯一键语义，
     * 因此在此处兜底为当前时间。
     *
     * @param r 影响记录
     * @return 参数数组
     */
    private static Object[] paramsOf(CityImpactRecord r) {
        LocalDateTime generated = r.getGeneratedAt() == null
                ? com.zfz.typhoon.util.DateUtil.now() : r.getGeneratedAt();
        return new Object[]{
                r.getTyphoonId(), r.getCityId(), r.getWindCircleLevel(), r.getEstimatedWindForce(),
                r.getImpactStartTime(), r.getImpactEndTime(), r.getDistanceKm(), generated
        };
    }

    /**
     * 供 Service 复用的条件构造入口（保留扩展点，当前未使用额外条件）。
     *
     * @param typhoonId 台风ID
     * @return SQL 构造器
     */
    static SqlBuilder baseQuery(long typhoonId) {
        return SqlBuilder.select(COLUMNS).from("city_impact_records r").eq("r.typhoon_id", Long.valueOf(typhoonId));
    }
}
