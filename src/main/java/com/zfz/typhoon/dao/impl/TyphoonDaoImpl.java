package com.zfz.typhoon.dao.impl;

import com.zfz.typhoon.dao.TyphoonDao;
import com.zfz.typhoon.dao.query.TyphoonQuery;
import com.zfz.typhoon.model.Typhoon;
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
 * 台风主表 DAO 实现（纯 JDBC + PreparedStatement）。
 *
 * <p>课程要求（阶段三 · ②「DAO 层使用 PreparedStatement，禁止字符串拼接 SQL」）。
 * 本类所有条件都通过 {@link SqlBuilder} 生成 {@code ?} 占位符，
 * 所有语句都经 {@link JdbcTemplate} 预编译执行，用户输入不参与 SQL 文本构造。
 *
 * <p>课程要求（阶段一 · 索引优化）：列表查询固定带 {@code ORDER BY generate_time DESC}，
 * 命中 {@code idx_typhoons_generate_time}；状态筛选命中 {@code idx_typhoons_status}；
 * 关键词检索对 {@code international_id}（唯一索引）走前缀匹配，
 * 对名称列走模糊匹配——后者无法走索引，故在 SQL 注释中说明取舍。
 *
 * @author 追风者项目组
 */
public class TyphoonDaoImpl implements TyphoonDao {

    /** 列清单（显式列出，避免 SELECT * 在加字段后破坏映射） */
    private static final String COLUMNS =
            "t.typhoon_id, t.international_id, t.chinese_name, t.english_name, t.basin, "
                    + "t.generate_time, t.dissipate_time, t.status, t.max_wind_force, "
                    + "t.max_wind_speed, t.min_pressure, t.is_historical_archive, "
                    + "t.created_at, t.updated_at";

    /** 行映射器：与列清单一一对应 */
    private static final RowMapper<Typhoon> MAPPER = new RowMapper<Typhoon>() {
        @Override
        public Typhoon map(ResultSet rs) throws SQLException {
            Typhoon t = new Typhoon();
            t.setTyphoonId(RsUtil.getLong(rs, "typhoon_id"));
            t.setInternationalId(RsUtil.getString(rs, "international_id"));
            t.setChineseName(RsUtil.getString(rs, "chinese_name"));
            t.setEnglishName(RsUtil.getString(rs, "english_name"));
            t.setBasin(RsUtil.getString(rs, "basin"));
            t.setGenerateTime(RsUtil.getDateTime(rs, "generate_time"));
            t.setDissipateTime(RsUtil.getDateTime(rs, "dissipate_time"));
            t.setStatus(RsUtil.getString(rs, "status"));
            t.setMaxWindForce(RsUtil.getInt(rs, "max_wind_force"));
            t.setMaxWindSpeed(RsUtil.getInt(rs, "max_wind_speed"));
            t.setMinPressure(RsUtil.getInt(rs, "min_pressure"));
            t.setHistoricalArchive(RsUtil.getBoolean(rs, "is_historical_archive"));
            t.setCreatedAt(RsUtil.getDateTime(rs, "created_at"));
            t.setUpdatedAt(RsUtil.getDateTime(rs, "updated_at"));
            return t;
        }
    };

    /* ------------------------------------------------------------------ */
    /*  单条查询                                                            */
    /* ------------------------------------------------------------------ */

    @Override
    public Typhoon findById(long typhoonId) {
        String sql = "SELECT " + COLUMNS + " FROM typhoons t WHERE t.typhoon_id = ?";
        return JdbcTemplate.queryOne(sql, MAPPER, Long.valueOf(typhoonId));
    }

    @Override
    public Typhoon findByInternationalId(String internationalId) {
        String sql = "SELECT " + COLUMNS + " FROM typhoons t WHERE t.international_id = ?";
        return JdbcTemplate.queryOne(sql, MAPPER, internationalId);
    }

    /* ------------------------------------------------------------------ */
    /*  列表与检索                                                          */
    /* ------------------------------------------------------------------ */

    @Override
    public List<Typhoon> listAll() {
        return JdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM typhoons t ORDER BY t.generate_time DESC, t.typhoon_id DESC",
                MAPPER);
    }

    @Override
    public List<Typhoon> listByStatus(String status) {
        return JdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM typhoons t WHERE t.status = ? "
                        + "ORDER BY t.generate_time DESC, t.typhoon_id DESC",
                MAPPER, status);
    }

    @Override
    public List<Typhoon> search(TyphoonQuery query) {
        SqlBuilder.Built built = buildListSql(query, false);
        return JdbcTemplate.query(built.getSql(), MAPPER, built.getParams());
    }

    @Override
    public int countByCondition(TyphoonQuery query) {
        SqlBuilder.Built built = buildListSql(query, true);
        Integer n = JdbcTemplate.queryInt(built.getSql(), built.getParams());
        return n == null ? 0 : n.intValue();
    }

    @Override
    public List<Integer> listYears() {
        // YEAR(generate_time) 为函数列，无法走索引；但本表数据量在万级以内，
        // 一次全表扫描换一个下拉框数据可接受，故不额外建函数索引（见 03_index_optimize.sql 说明）。
        return JdbcTemplate.query(
                "SELECT DISTINCT YEAR(generate_time) AS y FROM typhoons ORDER BY y DESC",
                new RowMapper<Integer>() {
                    @Override
                    public Integer map(ResultSet rs) throws SQLException {
                        return RsUtil.getInt(rs, "y");
                    }
                });
    }

    @Override
    public List<String> listBasins() {
        return JdbcTemplate.query(
                "SELECT DISTINCT basin FROM typhoons ORDER BY basin",
                new RowMapper<String>() {
                    @Override
                    public String map(ResultSet rs) throws SQLException {
                        return RsUtil.getString(rs, "basin");
                    }
                });
    }

    /* ------------------------------------------------------------------ */
    /*  统计                                                               */
    /* ------------------------------------------------------------------ */

    @Override
    public int countAll() {
        Integer n = JdbcTemplate.queryInt("SELECT COUNT(*) FROM typhoons");
        return n == null ? 0 : n.intValue();
    }

    @Override
    public int countByStatus(String status) {
        Integer n = JdbcTemplate.queryInt("SELECT COUNT(*) FROM typhoons WHERE status = ?", status);
        return n == null ? 0 : n.intValue();
    }

    @Override
    public int countArchived() {
        Integer n = JdbcTemplate.queryInt(
                "SELECT COUNT(*) FROM typhoons WHERE is_historical_archive = 1");
        return n == null ? 0 : n.intValue();
    }

    /* ------------------------------------------------------------------ */
    /*  写入                                                               */
    /* ------------------------------------------------------------------ */

    @Override
    public long insert(Typhoon t) {
        String sql = "INSERT INTO typhoons (international_id, chinese_name, english_name, basin, "
                + "generate_time, dissipate_time, status, max_wind_force, max_wind_speed, "
                + "min_pressure, is_historical_archive) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Long id = JdbcTemplate.insert(sql,
                t.getInternationalId(), t.getChineseName(), t.getEnglishName(),
                t.getBasin() == null ? "西北太平洋" : t.getBasin(),
                t.getGenerateTime(), t.getDissipateTime(),
                t.getStatus() == null ? STATUS_ACTIVE : t.getStatus(),
                t.getMaxWindForce(), t.getMaxWindSpeed(), t.getMinPressure(),
                Boolean.valueOf(t.getHistoricalArchive() != null && t.getHistoricalArchive().booleanValue()));
        return id == null ? 0L : id.longValue();
    }

    @Override
    public int update(Typhoon t) {
        String sql = "UPDATE typhoons SET international_id = ?, chinese_name = ?, english_name = ?, "
                + "basin = ?, generate_time = ?, dissipate_time = ?, status = ?, "
                + "max_wind_force = ?, max_wind_speed = ?, min_pressure = ?, "
                + "is_historical_archive = ? WHERE typhoon_id = ?";
        return JdbcTemplate.update(sql,
                t.getInternationalId(), t.getChineseName(), t.getEnglishName(), t.getBasin(),
                t.getGenerateTime(), t.getDissipateTime(), t.getStatus(),
                t.getMaxWindForce(), t.getMaxWindSpeed(), t.getMinPressure(),
                Boolean.valueOf(t.getHistoricalArchive() != null && t.getHistoricalArchive().booleanValue()),
                t.getTyphoonId());
    }

    @Override
    public int refreshIntensityExtremes(long typhoonId) {
        // 一条语句内完成"从轨迹表取极值 → 回写主表"，避免应用层来回；
        // 子查询在 typhoon_id 上命中 uk_tracks_typhoon_time 的前缀，效率可接受。
        String sql = "UPDATE typhoons t SET "
                + "t.max_wind_force = (SELECT MAX(tr.wind_force) FROM typhoon_tracks tr WHERE tr.typhoon_id = t.typhoon_id), "
                + "t.max_wind_speed = (SELECT MAX(tr.wind_speed) FROM typhoon_tracks tr WHERE tr.typhoon_id = t.typhoon_id), "
                + "t.min_pressure  = (SELECT MIN(tr.pressure)  FROM typhoon_tracks tr WHERE tr.typhoon_id = t.typhoon_id) "
                + "WHERE t.typhoon_id = ?";
        return JdbcTemplate.update(sql, Long.valueOf(typhoonId));
    }

    @Override
    public int archive(long typhoonId, LocalDateTime dissipateTime) {
        String sql = "UPDATE typhoons SET status = ?, dissipate_time = ?, is_historical_archive = 1 "
                + "WHERE typhoon_id = ?";
        return JdbcTemplate.update(sql, STATUS_FINISHED, dissipateTime, Long.valueOf(typhoonId));
    }

    @Override
    public int deleteById(long typhoonId) {
        return JdbcTemplate.update("DELETE FROM typhoons WHERE typhoon_id = ?", Long.valueOf(typhoonId));
    }

    /* ------------------------------------------------------------------ */
    /*  内部实现                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * 构造列表 / 计数 SQL。
     *
     * @param query     查询条件
     * @param forCount  true 时只生成 COUNT 与相同 WHERE，不拼排序分页
     * @return 生成的 SQL 与参数
     */
    private SqlBuilder.Built buildListSql(TyphoonQuery query, boolean forCount) {
        TyphoonQuery q = query == null ? new TyphoonQuery() : query;

        SqlBuilder sb = forCount
                ? SqlBuilder.select("COUNT(*)").from("typhoons t")
                : SqlBuilder.select(COLUMNS).from("typhoons t");

        // 状态：精确匹配，走 idx_typhoons_status
        sb.eq("t.status", q.getStatus());
        // 海域：精确匹配，走 idx_typhoons_basin
        sb.eq("t.basin", q.getBasin());

        // 年份：用范围条件代替 YEAR() 函数，保证 idx_typhoons_generate_time 可用
        if (q.getYear() != null) {
            int y = q.getYear().intValue();
            sb.raw("t.generate_time >= ? AND t.generate_time < ?",
                    LocalDateTime.of(y, 1, 1, 0, 0, 0),
                    LocalDateTime.of(y + 1, 1, 1, 0, 0, 0));
        }
        // 显式时间区间（与年份条件可叠加，语义为"交集"）
        sb.ge("t.generate_time", q.getGenerateFrom());
        sb.le("t.generate_time", q.getGenerateTo());

        // 归档标记：精确匹配，走 idx_typhoons_archive
        sb.eqBool("t.is_historical_archive", q.getArchived());

        // 强度下限：max_wind_force 无独立索引，作为过滤条件跟在索引条件之后
        sb.ge("t.max_wind_force", q.getMinWindForce());

        // 关键词：国际编号走唯一索引前缀匹配；名称列模糊匹配无法走索引，
        // 但列表已由年份/状态等强筛选条件大幅缩小结果集，实测可接受。
        String kw = q.getKeyword();
        if (kw != null && !kw.trim().isEmpty()) {
            String k = kw.trim();
            sb.raw("(t.chinese_name LIKE ? OR t.english_name LIKE ? OR t.international_id LIKE ?)",
                    "%" + k + "%", "%" + k + "%", k + "%");
        }

        if (forCount) {
            return sb.build();
        }

        sb.orderBy(resolveOrderBy(q.getSort()));
        if (q.getLimit() > 0) {
            sb.limit(q.getLimit(), q.getOffset());
        }
        return sb.build();
    }

    /**
     * 排序白名单。
     *
     * <p><b>安全要点</b>：order by 的列名无法用占位符绑定，
     * 因此必须走白名单映射，绝不能把前端字符串直接拼进 SQL，
     * 否则会形成注入点。未命中白名单时回退到默认排序。
     *
     * @param sort 前端传入的排序标识
     * @return 安全的 ORDER BY 子句
     */
    private String resolveOrderBy(String sort) {
        List<String> allowed = new ArrayList<String>();
        allowed.add("time_desc");
        allowed.add("time_asc");
        allowed.add("power_desc");
        allowed.add("name");

        String s = sort == null ? "" : sort.trim();
        if (!allowed.contains(s)) {
            s = "time_desc";
        }
        if ("time_asc".equals(s)) {
            return "t.generate_time ASC, t.typhoon_id ASC";
        }
        if ("power_desc".equals(s)) {
            return "t.max_wind_force DESC, t.generate_time DESC";
        }
        if ("name".equals(s)) {
            // 中文按 utf8mb4_unicode_ci 排序，近似拼音/笔画顺序
            return "t.chinese_name ASC";
        }
        return "t.generate_time DESC, t.typhoon_id DESC";
    }
}
