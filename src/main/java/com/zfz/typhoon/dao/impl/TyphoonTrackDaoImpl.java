package com.zfz.typhoon.dao.impl;

import com.zfz.typhoon.dao.TyphoonTrackDao;
import com.zfz.typhoon.model.TyphoonTrack;
import com.zfz.typhoon.util.JdbcTemplate;
import com.zfz.typhoon.util.RowMapper;
import com.zfz.typhoon.util.RsUtil;
import com.zfz.typhoon.util.SqlBuilder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 台风轨迹表 DAO 实现。
 *
 * <p>课程要求（阶段三 · ②「PreparedStatement」；阶段五 ·「批量入库」）。
 *
 * @author 追风者项目组
 */
public class TyphoonTrackDaoImpl implements TyphoonTrackDao {

    private static final String COLUMNS =
            "tr.track_id, tr.typhoon_id, tr.obs_time, tr.latitude, tr.longitude, "
                    + "tr.wind_force, tr.wind_speed, tr.pressure, tr.move_direction, tr.move_speed";

    private static final RowMapper<TyphoonTrack> MAPPER = new RowMapper<TyphoonTrack>() {
        @Override
        public TyphoonTrack map(ResultSet rs) throws SQLException {
            TyphoonTrack t = new TyphoonTrack();
            t.setTrackId(RsUtil.getLong(rs, "track_id"));
            t.setTyphoonId(RsUtil.getLong(rs, "typhoon_id"));
            t.setObsTime(RsUtil.getDateTime(rs, "obs_time"));
            t.setLatitude(RsUtil.getDouble(rs, "latitude"));
            t.setLongitude(RsUtil.getDouble(rs, "longitude"));
            t.setWindForce(RsUtil.getInt(rs, "wind_force"));
            t.setWindSpeed(RsUtil.getInt(rs, "wind_speed"));
            t.setPressure(RsUtil.getInt(rs, "pressure"));
            t.setMoveDirection(RsUtil.getDouble(rs, "move_direction"));
            t.setMoveSpeed(RsUtil.getDouble(rs, "move_speed"));
            return t;
        }
    };

    /** 单条 INSERT 的列清单与占位符（批量与单条共用，保证语义一致） */
    private static final String INSERT_SQL =
            "INSERT INTO typhoon_tracks (typhoon_id, obs_time, latitude, longitude, wind_force, "
                    + "wind_speed, pressure, move_direction, move_speed) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /** 幂等 INSERT：命中 uk_tracks_typhoon_time 时改为更新实况字段 */
    private static final String UPSERT_SQL =
            "INSERT INTO typhoon_tracks (typhoon_id, obs_time, latitude, longitude, wind_force, "
                    + "wind_speed, pressure, move_direction, move_speed) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE latitude = VALUES(latitude), longitude = VALUES(longitude), "
                    + "wind_force = VALUES(wind_force), wind_speed = VALUES(wind_speed), "
                    + "pressure = VALUES(pressure), move_direction = VALUES(move_direction), "
                    + "move_speed = VALUES(move_speed)";

    /* ------------------------------------------------------------------ */
    /*  查询                                                               */
    /* ------------------------------------------------------------------ */

    @Override
    public List<TyphoonTrack> listByTyphoon(long typhoonId) {
        // ORDER BY obs_time ASC 命中 uk_tracks_typhoon_time，天然有序，无需额外排序开销
        String sql = "SELECT " + COLUMNS + " FROM typhoon_tracks tr "
                + "WHERE tr.typhoon_id = ? ORDER BY tr.obs_time ASC";
        return JdbcTemplate.query(sql, MAPPER, Long.valueOf(typhoonId));
    }

    @Override
    public List<TyphoonTrack> listByTyphoonBetween(long typhoonId, LocalDateTime from, LocalDateTime to) {
        SqlBuilder sb = SqlBuilder.select(COLUMNS).from("typhoon_tracks tr")
                .eq("tr.typhoon_id", Long.valueOf(typhoonId))
                .ge("tr.obs_time", from)
                .le("tr.obs_time", to)
                .orderBy("tr.obs_time ASC");
        SqlBuilder.Built built = sb.build();
        return JdbcTemplate.query(built.getSql(), MAPPER, built.getParams());
    }

    @Override
    public TyphoonTrack findLatest(long typhoonId) {
        String sql = "SELECT " + COLUMNS + " FROM typhoon_tracks tr "
                + "WHERE tr.typhoon_id = ? ORDER BY tr.obs_time DESC LIMIT 1";
        return JdbcTemplate.queryOne(sql, MAPPER, Long.valueOf(typhoonId));
    }

    @Override
    public TyphoonTrack findFirst(long typhoonId) {
        String sql = "SELECT " + COLUMNS + " FROM typhoon_tracks tr "
                + "WHERE tr.typhoon_id = ? ORDER BY tr.obs_time ASC LIMIT 1";
        return JdbcTemplate.queryOne(sql, MAPPER, Long.valueOf(typhoonId));
    }

    @Override
    public List<TyphoonTrack> listLatestByTyphoons(Collection<Long> typhoonIds) {
        if (typhoonIds == null || typhoonIds.isEmpty()) {
            return new ArrayList<TyphoonTrack>();
        }
        // 派生表先算出每台风的最大观测时间（只扫索引），再回表取整行。
        // 这样无论有多少个台风，往返次数恒为 1。
        SqlBuilder sb = SqlBuilder.select(COLUMNS).from("typhoon_tracks tr"
                + " JOIN (SELECT typhoon_id, MAX(obs_time) AS mt FROM typhoon_tracks"
                + " WHERE typhoon_id IN (" + placeholders(typhoonIds.size()) + ")"
                + " GROUP BY typhoon_id) x"
                + " ON x.typhoon_id = tr.typhoon_id AND x.mt = tr.obs_time");
        // IN 子句的占位符已手工展开，参数按原顺序注入
        SqlBuilder.Built built = sb.build();
        Object[] merged = new Object[typhoonIds.size() + built.getParams().length];
        int i = 0;
        for (Long id : typhoonIds) {
            merged[i++] = id;
        }
        for (Object p : built.getParams()) {
            merged[i++] = p;
        }
        return JdbcTemplate.query(built.getSql(), MAPPER, merged);
    }

    @Override
    public int countByTyphoon(long typhoonId) {
        Integer n = JdbcTemplate.queryInt(
                "SELECT COUNT(*) FROM typhoon_tracks WHERE typhoon_id = ?", Long.valueOf(typhoonId));
        return n == null ? 0 : n.intValue();
    }

    @Override
    public double spanHours(long typhoonId) {
        // MIN/MAX 均在 (typhoon_id, obs_time) 索引上完成
        String sql = "SELECT TIMESTAMPDIFF(MINUTE, MIN(obs_time), MAX(obs_time)) AS m "
                + "FROM typhoon_tracks WHERE typhoon_id = ?";
        Integer minutes = JdbcTemplate.queryInt(sql, Long.valueOf(typhoonId));
        if (minutes == null || minutes.intValue() <= 0) {
            return 0d;
        }
        return minutes.doubleValue() / 60d;
    }

    /* ------------------------------------------------------------------ */
    /*  写入                                                               */
    /* ------------------------------------------------------------------ */

    @Override
    public long insert(TyphoonTrack t) {
        Long id = JdbcTemplate.insert(INSERT_SQL, paramsOf(t));
        return id == null ? 0L : id.longValue();
    }

    @Override
    public int batchInsert(List<TyphoonTrack> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return 0;
        }
        List<Object[]> rows = new ArrayList<Object[]>(tracks.size());
        for (TyphoonTrack t : tracks) {
            rows.add(paramsOf(t));
        }
        return JdbcTemplate.batchUpdate(INSERT_SQL, rows);
    }

    @Override
    public int upsertAll(List<TyphoonTrack> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return 0;
        }
        List<Object[]> rows = new ArrayList<Object[]>(tracks.size());
        for (TyphoonTrack t : tracks) {
            rows.add(paramsOf(t));
        }
        return JdbcTemplate.batchUpdate(UPSERT_SQL, rows);
    }

    @Override
    public int deleteByTyphoonAndTime(long typhoonId, LocalDateTime obsTime) {
        return JdbcTemplate.update(
                "DELETE FROM typhoon_tracks WHERE typhoon_id = ? AND obs_time = ?",
                Long.valueOf(typhoonId), obsTime);
    }

    /* ------------------------------------------------------------------ */
    /*  内部实现                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * 把一个轨迹点摊平成参数数组（顺序必须与 SQL 占位符一致）。
     *
     * @param t 轨迹点
     * @return 参数数组
     */
    private static Object[] paramsOf(TyphoonTrack t) {
        return new Object[]{
                t.getTyphoonId(), t.getObsTime(), t.getLatitude(), t.getLongitude(),
                t.getWindForce(), t.getWindSpeed(), t.getPressure(),
                t.getMoveDirection(), t.getMoveSpeed()
        };
    }

    /**
     * 生成 {@code ?, ?, ?} 形式的占位符串。
     *
     * <p>注意：这里拼接的是<b>占位符本身</b>而非用户数据，
     * 参数仍通过 JDBC 绑定，不构成注入风险。
     *
     * @param count 占位符数量
     * @return 占位符串
     */
    private static String placeholders(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('?');
        }
        return sb.toString();
    }

    /**
     * 计算两个时刻之间的小时数（供 Service 做插值步长估算）。
     *
     * @param from 起点
     * @param to   终点
     * @return 小时数
     */
    public static double hoursBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return 0d;
        }
        return Duration.between(from, to).toMinutes() / 60d;
    }
}
