package com.zfz.typhoon.dao.impl;

import com.zfz.typhoon.dao.WindCircleDao;
import com.zfz.typhoon.model.WindCircle;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 风圈半径表 DAO 实现。
 *
 * <p>课程要求（阶段三 · ②「PreparedStatement」；阶段四 ·「风圈四象限」）。
 *
 * @author 追风者项目组
 */
public class WindCircleDaoImpl implements WindCircleDao {

    private static final String COLUMNS =
            "w.circle_id, w.typhoon_id, w.obs_time, "
                    + "w.level7_ne, w.level7_nw, w.level7_se, w.level7_sw, "
                    + "w.level10_ne, w.level10_nw, w.level10_se, w.level10_sw, "
                    + "w.level12_ne, w.level12_nw, w.level12_se, w.level12_sw, w.created_at";

    private static final RowMapper<WindCircle> MAPPER = new RowMapper<WindCircle>() {
        @Override
        public WindCircle map(ResultSet rs) throws SQLException {
            WindCircle w = new WindCircle();
            w.setCircleId(RsUtil.getLong(rs, "circle_id"));
            w.setTyphoonId(RsUtil.getLong(rs, "typhoon_id"));
            w.setObsTime(RsUtil.getDateTime(rs, "obs_time"));
            w.setLevel7Ne(RsUtil.getInt(rs, "level7_ne"));
            w.setLevel7Nw(RsUtil.getInt(rs, "level7_nw"));
            w.setLevel7Se(RsUtil.getInt(rs, "level7_se"));
            w.setLevel7Sw(RsUtil.getInt(rs, "level7_sw"));
            w.setLevel10Ne(RsUtil.getInt(rs, "level10_ne"));
            w.setLevel10Nw(RsUtil.getInt(rs, "level10_nw"));
            w.setLevel10Se(RsUtil.getInt(rs, "level10_se"));
            w.setLevel10Sw(RsUtil.getInt(rs, "level10_sw"));
            w.setLevel12Ne(RsUtil.getInt(rs, "level12_ne"));
            w.setLevel12Nw(RsUtil.getInt(rs, "level12_nw"));
            w.setLevel12Se(RsUtil.getInt(rs, "level12_se"));
            w.setLevel12Sw(RsUtil.getInt(rs, "level12_sw"));
            w.setCreatedAt(RsUtil.getDateTime(rs, "created_at"));
            return w;
        }
    };

    /** 12 个半径列 + 台风 + 时刻，供 INSERT 与 UPSERT 共用 */
    private static final String INSERT_COLUMNS =
            "(typhoon_id, obs_time, "
                    + "level7_ne, level7_nw, level7_se, level7_sw, "
                    + "level10_ne, level10_nw, level10_se, level10_sw, "
                    + "level12_ne, level12_nw, level12_se, level12_sw) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_SQL = "INSERT INTO wind_circles " + INSERT_COLUMNS;

    /** 命中唯一键 uk_circles_typhoon_time 时改为更新半径，实现重复同步不脏数据 */
    private static final String UPSERT_SQL = "INSERT INTO wind_circles " + INSERT_COLUMNS
            + " ON DUPLICATE KEY UPDATE "
            + "level7_ne = VALUES(level7_ne), level7_nw = VALUES(level7_nw), "
            + "level7_se = VALUES(level7_se), level7_sw = VALUES(level7_sw), "
            + "level10_ne = VALUES(level10_ne), level10_nw = VALUES(level10_nw), "
            + "level10_se = VALUES(level10_se), level10_sw = VALUES(level10_sw), "
            + "level12_ne = VALUES(level12_ne), level12_nw = VALUES(level12_nw), "
            + "level12_se = VALUES(level12_se), level12_sw = VALUES(level12_sw)";

    /* ------------------------------------------------------------------ */
    /*  查询                                                               */
    /* ------------------------------------------------------------------ */

    @Override
    public WindCircle findLatest(long typhoonId) {
        String sql = "SELECT " + COLUMNS + " FROM wind_circles w "
                + "WHERE w.typhoon_id = ? ORDER BY w.obs_time DESC LIMIT 1";
        return JdbcTemplate.queryOne(sql, MAPPER, Long.valueOf(typhoonId));
    }

    @Override
    public WindCircle findByTyphoonAndTime(long typhoonId, LocalDateTime obsTime) {
        // 命中唯一键 uk_circles_typhoon_time，等值查询最快
        String sql = "SELECT " + COLUMNS + " FROM wind_circles w "
                + "WHERE w.typhoon_id = ? AND w.obs_time = ?";
        return JdbcTemplate.queryOne(sql, MAPPER, Long.valueOf(typhoonId), obsTime);
    }

    @Override
    public List<WindCircle> listByTyphoon(long typhoonId) {
        String sql = "SELECT " + COLUMNS + " FROM wind_circles w "
                + "WHERE w.typhoon_id = ? ORDER BY w.obs_time ASC";
        return JdbcTemplate.query(sql, MAPPER, Long.valueOf(typhoonId));
    }

    @Override
    public List<WindCircle> listLatestByTyphoons(Collection<Long> typhoonIds) {
        if (typhoonIds == null || typhoonIds.isEmpty()) {
            return new ArrayList<WindCircle>();
        }
        // 同轨迹表策略：派生表取每台风最大 obs_time，回表取整行，一次往返
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < typhoonIds.size(); i++) {
            if (i > 0) {
                in.append(", ");
            }
            in.append('?');
        }
        String sql = "SELECT " + COLUMNS + " FROM wind_circles w "
                + "JOIN (SELECT typhoon_id, MAX(obs_time) AS mt FROM wind_circles "
                + "WHERE typhoon_id IN (" + in + ") GROUP BY typhoon_id) x "
                + "ON x.typhoon_id = w.typhoon_id AND x.mt = w.obs_time";
        return JdbcTemplate.query(sql, MAPPER, typhoonIds.toArray());
    }

    /* ------------------------------------------------------------------ */
    /*  写入                                                               */
    /* ------------------------------------------------------------------ */

    @Override
    public long insert(WindCircle c) {
        Long id = JdbcTemplate.insert(INSERT_SQL, paramsOf(c));
        return id == null ? 0L : id.longValue();
    }

    @Override
    public int upsert(WindCircle c) {
        return JdbcTemplate.update(UPSERT_SQL, paramsOf(c));
    }

    @Override
    public int deleteByTyphoonAndTime(long typhoonId, LocalDateTime obsTime) {
        return JdbcTemplate.update(
                "DELETE FROM wind_circles WHERE typhoon_id = ? AND obs_time = ?",
                Long.valueOf(typhoonId), obsTime);
    }

    /* ------------------------------------------------------------------ */
    /*  内部实现                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * 把风圈对象摊平为 14 个参数，顺序与 {@code INSERT_COLUMNS} 严格一致。
     *
     * <p>半径为空时写 0（列定义为 {@code NOT NULL}），
     * 由 {@link WindCircle#nz} 的语义决定（null 视为无风圈，即 0 km）。
     *
     * @param c 风圈记录
     * @return 参数数组
     */
    private static Object[] paramsOf(WindCircle c) {
        return new Object[]{
                c.getTyphoonId(), c.getObsTime(),
                nz(c.getLevel7Ne()), nz(c.getLevel7Nw()), nz(c.getLevel7Se()), nz(c.getLevel7Sw()),
                nz(c.getLevel10Ne()), nz(c.getLevel10Nw()), nz(c.getLevel10Se()), nz(c.getLevel10Sw()),
                nz(c.getLevel12Ne()), nz(c.getLevel12Nw()), nz(c.getLevel12Se()), nz(c.getLevel12Sw())
        };
    }

    /**
     * null → 0（数据库列为 NOT NULL）。
     *
     * @param v 半径值
     * @return 非空整数
     */
    private static Integer nz(Integer v) {
        return v == null ? Integer.valueOf(0) : v;
    }
}
