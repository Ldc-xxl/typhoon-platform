package com.zfz.typhoon.util;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 轻量 JDBC 模板：统一处理「拿连接 → 设参 → 执行 → 映射 → 关资源 → 包异常」。
 *
 * <p>课程要求（阶段二 · ③「工具类」；阶段三 · ②「必须在 DAO 层消灭重复样板」）。
 * 项目禁止引入 Spring 等框架，因此这里手写一个极简版 {@code JdbcTemplate}，
 * 让每个 DAO 方法只保留<b>真正的业务语义</b>（SQL 与字段映射）。
 *
 * <h3>为什么不用 try-with-resources 直接写</h3>
 * <ul>
 *   <li>26 张表、上百个方法，逐个写 7 行样板会让代码量翻三倍；</li>
 *   <li>关闭顺序、{@code wasNull()} 处理、异常包装极易遗漏；</li>
 *   <li>集中一处后，观察 SQL 日志、统计耗时、后续加缓存都有唯一入口。</li>
 * </ul>
 *
 * <h3>安全要求（课程评审点）</h3>
 * <b>所有</b>方法都使用 {@link PreparedStatement} 预编译，参数一律通过
 * {@link #bind} 绑定，<b>禁止</b>在 DAO 里拼接用户输入。动态条件由
 * {@link SqlBuilder} 生成"占位符 + 参数数组"，同样不破坏该约束。
 *
 * <h3>异常契约</h3>
 * 所有 {@link SQLException} 一律包装为 {@link DataAccessException} 抛出
 * （运行时异常），由 Servlet 层统一兜底为 {@code {code:500}}，
 * DAO 层签名因此保持干净，不必 {@code throws SQLException}。
 *
 * <h3>事务</h3>
 * 本类只调 {@link DBUtil#getConnection()}，<b>不</b>主动开启或提交事务。
 * 若上层（Service）已调用 {@code DBUtil.beginTransaction()}，
 * 这里拿到的就是同一个事务连接，多次 DAO 调用自动处于同一事务中。
 *
 * @author 追风者项目组
 * @see RowMapper
 * @see SqlBuilder
 * @see DBUtil
 */
public final class JdbcTemplate {

    private JdbcTemplate() {
        // 工具类禁止实例化
    }

    /* ------------------------------------------------------------------ */
    /*  查询                                                                */
    /* ------------------------------------------------------------------ */

    /**
     * 查询列表。
     *
     * @param sql    SQL（含 {@code ?} 占位符）
     * @param mapper 行映射器
     * @param params 参数
     * @param <T>    结果类型
     * @return 结果列表，永不为 null
     * @throws DataAccessException SQL 执行失败
     */
    public static <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        long start = System.currentTimeMillis();
        DBUtil.logSql(sql, params);
        List<T> list = new ArrayList<T>();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            bind(ps, params);
            rs = ps.executeQuery();
            while (rs.next()) {
                T item = mapper.map(rs);
                if (item != null) {
                    list.add(item);
                }
            }
            logCost(sql, start, list.size());
            return list;
        } catch (SQLException e) {
            throw wrap("查询", sql, e);
        } finally {
            DBUtil.close(rs, ps, conn);
        }
    }

    /**
     * 查询单个对象，无结果返回 null。
     *
     * <p><b>为什么这里必须做"幂等追加"而不是无脑拼接 {@code LIMIT 1}</b>：
     * 大量 DAO 为了"取最新一条"已经在自己 SQL 的末尾写了 {@code LIMIT 1}
     * （例如 {@code TyphoonTrackDaoImpl#findLatest}、{@code WindCircleDaoImpl#findLatest}）。
     * 如果本方法再无脑拼一次，生成的 SQL 会变成
     * {@code ... ORDER BY obs_time DESC LIMIT 1 LIMIT 1}，
     * MySQL 直接抛语法错误：
     * {@code You have an error in your SQL syntax ... near 'LIMIT 1'}。
     *
     * <p>这类错误的排查成本特别高，因为报错位置只指向<b>末尾</b>，
     * 而 SQL 日志里那条语句看起来"明明是对的"——肉眼很容易漏掉重复的
     * {@code LIMIT 1}，转而怀疑表结构或字段名。
     * 因此这里统一改为幂等追加：<b>已有 LIMIT 就原样使用，没有才补一个</b>。
     * 这样两种写法（DAO 自带 / 不自带）都能正确工作。
     *
     * @param sql    SQL
     * @param mapper 行映射器
     * @param params 参数
     * @param <T>    结果类型
     * @return 单个对象或 null
     * @throws DataAccessException SQL 执行失败
     */
    public static <T> T queryOne(String sql, RowMapper<T> mapper, Object... params) {
        List<T> list = query(withLimitOne(sql), mapper, params);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 匹配 SQL 末尾的 LIMIT 子句，覆盖 MySQL 支持的三种写法：
     * {@code LIMIT n}、{@code LIMIT n, m}、以及预编译占位符 {@code LIMIT ?}。
     * 允许末尾出现分号与空白，避免 {@code "... LIMIT 1;"} 被漏判。
     */
    private static final Pattern TRAILING_LIMIT =
            Pattern.compile("(?is)\\bLIMIT\\s+(\\d+|\\?)(\\s*,\\s*(\\d+|\\?))?\\s*;?\\s*$");

    /**
     * 保证一条 SQL 最多只带一个 LIMIT 子句。
     *
     * @param sql 原始 SQL
     * @return 已有 LIMIT 时原样返回；否则追加 {@code LIMIT 1}
     */
    private static String withLimitOne(String sql) {
        if (sql == null) {
            return "";
        }
        String trimmed = sql.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        return TRAILING_LIMIT.matcher(trimmed).find() ? trimmed : trimmed + " LIMIT 1";
    }

    /**
     * 查询一个整数标量（如 {@code COUNT(*)}），无结果返回 null。
     *
     * @param sql    SQL
     * @param params 参数
     * @return Integer 或 null
     * @throws DataAccessException SQL 执行失败
     */
    public static Integer queryInt(String sql, Object... params) {
        Number n = queryNumber(sql, params);
        return n == null ? null : Integer.valueOf(n.intValue());
    }

    /**
     * 查询一个长整数标量，无结果返回 null。
     *
     * @param sql    SQL
     * @param params 参数
     * @return Long 或 null
     * @throws DataAccessException SQL 执行失败
     */
    public static Long queryLong(String sql, Object... params) {
        Number n = queryNumber(sql, params);
        return n == null ? null : Long.valueOf(n.longValue());
    }

    /**
     * 查询一个浮点标量（如 {@code AVG(x)}），无结果返回 null。
     *
     * @param sql    SQL
     * @param params 参数
     * @return Double 或 null
     * @throws DataAccessException SQL 执行失败
     */
    public static Double queryDouble(String sql, Object... params) {
        Number n = queryNumber(sql, params);
        return n == null ? null : Double.valueOf(n.doubleValue());
    }

    /**
     * 查询一个字符串标量（如 {@code MAX(obs_time)}）。
     *
     * @param sql    SQL
     * @param params 参数
     * @return 字符串或 null
     * @throws DataAccessException SQL 执行失败
     */
    public static String queryString(String sql, Object... params) {
        long start = System.currentTimeMillis();
        DBUtil.logSql(sql, params);
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            bind(ps, params);
            rs = ps.executeQuery();
            String result = rs.next() ? rs.getString(1) : null;
            logCost(sql, start, result == null ? 0 : 1);
            return result;
        } catch (SQLException e) {
            throw wrap("查询", sql, e);
        } finally {
            DBUtil.close(rs, ps, conn);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  写入                                                                */
    /* ------------------------------------------------------------------ */

    /**
     * 执行 INSERT / UPDATE / DELETE，返回受影响行数。
     *
     * @param sql    SQL
     * @param params 参数
     * @return 受影响行数
     * @throws DataAccessException SQL 执行失败
     */
    public static int update(String sql, Object... params) {
        long start = System.currentTimeMillis();
        DBUtil.logSql(sql, params);
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            bind(ps, params);
            int affected = ps.executeUpdate();
            logCost(sql, start, affected);
            return affected;
        } catch (SQLException e) {
            throw wrap("更新", sql, e);
        } finally {
            DBUtil.close(null, ps, conn);
        }
    }

    /**
     * 执行 INSERT 并返回自增主键。
     *
     * @param sql    INSERT 语句
     * @param params 参数
     * @return 新生成的主键；驱动未返回时返回 null
     * @throws DataAccessException SQL 执行失败
     */
    public static Long insert(String sql, Object... params) {
        long start = System.currentTimeMillis();
        DBUtil.logSql(sql, params);
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            bind(ps, params);
            ps.executeUpdate();
            rs = ps.getGeneratedKeys();
            Long id = rs.next() ? Long.valueOf(rs.getLong(1)) : null;
            logCost(sql, start, 1);
            return id;
        } catch (SQLException e) {
            throw wrap("新增", sql, e);
        } finally {
            DBUtil.close(rs, ps, conn);
        }
    }

    /**
     * 批量执行同一条 SQL（如批量插入轨迹点）。
     *
     * <p>整批失败时由驱动决定已执行部分是否回滚；如需强一致，
     * 请在 Service 层用 {@code DBUtil.beginTransaction()} 包住本调用。
     *
     * @param sql        SQL
     * @param batchParams 参数行集合，每行长度必须与占位符数量一致
     * @return 实际执行的行数之和
     * @throws DataAccessException SQL 执行失败
     */
    public static int batchUpdate(String sql, List<Object[]> batchParams) {
        if (batchParams == null || batchParams.isEmpty()) {
            return 0;
        }
        long start = System.currentTimeMillis();
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            for (Object[] row : batchParams) {
                bind(ps, row);
                ps.addBatch();
            }
            int[] counts = ps.executeBatch();
            int total = 0;
            for (int c : counts) {
                if (c > 0) {
                    total += c;
                }
            }
            DBUtil.logSql(sql + " [batch x" + batchParams.size() + "]", null);
            logCost(sql, start, total);
            return total;
        } catch (SQLException e) {
            throw wrap("批量更新", sql, e);
        } finally {
            DBUtil.close(null, ps, conn);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  内部实现                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * 查询单列数值标量，统一转为 Number。
     */
    private static Number queryNumber(String sql, Object... params) {
        long start = System.currentTimeMillis();
        DBUtil.logSql(sql, params);
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DBUtil.getConnection();
            ps = conn.prepareStatement(sql);
            bind(ps, params);
            rs = ps.executeQuery();
            // 注意：这里不能在 rs.next() 返回 false 之后调用 wasNull()。
            // JDBC 规范中 wasNull() 只在"刚刚读取过某一行"时才有意义；
            // MySQL Connector/J 在空结果集上调用会直接抛 NullPointerException
            // （"Cannot invoke ...Row.wasNull() because this.thisRow is null"）。
            // 典型触发场景是 queryInt("SELECT id FROM t WHERE name = ?") 这类
            // 查不到就一行都没有的语句。且无论读不读，getObject 对 SQL NULL
            // 本来就返回 null，wasNull 在这里是多余的。
            Number result = rs.next() ? (Number) rs.getObject(1) : null;
            logCost(sql, start, result == null ? 0 : 1);
            return result;
        } catch (SQLException e) {
            throw wrap("查询", sql, e);
        } finally {
            DBUtil.close(rs, ps, conn);
        }
    }

    /**
     * 按顺序绑定参数。支持的类型：
     * null / String / Integer / Long / Double / Float / Short / Byte /
     * Boolean（映射为 0、1）/ BigDecimal / LocalDateTime / LocalDate / java.util.Date。
     *
     * @param ps     预编译语句
     * @param params 参数数组
     * @throws SQLException 绑定失败
     */
    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.length; i++) {
            Object p = params[i];
            int idx = i + 1;
            if (p == null) {
                ps.setObject(idx, null);
            } else if (p instanceof String) {
                ps.setString(idx, (String) p);
            } else if (p instanceof Integer) {
                ps.setInt(idx, ((Integer) p).intValue());
            } else if (p instanceof Long) {
                ps.setLong(idx, ((Long) p).longValue());
            } else if (p instanceof Double) {
                ps.setDouble(idx, ((Double) p).doubleValue());
            } else if (p instanceof Float) {
                ps.setDouble(idx, ((Float) p).doubleValue());
            } else if (p instanceof Short) {
                ps.setInt(idx, ((Short) p).intValue());
            } else if (p instanceof Byte) {
                ps.setInt(idx, ((Byte) p).intValue());
            } else if (p instanceof BigDecimal) {
                ps.setBigDecimal(idx, (BigDecimal) p);
            } else if (p instanceof Boolean) {
                ps.setInt(idx, ((Boolean) p).booleanValue() ? 1 : 0);
            } else if (p instanceof LocalDateTime) {
                ps.setTimestamp(idx, Timestamp.valueOf((LocalDateTime) p));
            } else if (p instanceof LocalDate) {
                ps.setTimestamp(idx, Timestamp.valueOf(((LocalDate) p).atStartOfDay()));
            } else if (p instanceof java.util.Date) {
                ps.setTimestamp(idx, new Timestamp(((java.util.Date) p).getTime()));
            } else {
                ps.setObject(idx, p);
            }
        }
    }

    /**
     * 详细日志（仅 sql.show=true 时输出），含耗时。
     */
    private static void logCost(String sql, long start, int rows) {
        if (DBUtil.isShowSql()) {
            System.out.println("[SQL] " + (System.currentTimeMillis() - start)
                    + "ms  rows=" + rows + "  " + firstLine(sql));
        }
    }

    private static String firstLine(String sql) {
        int nl = sql.indexOf('\n');
        return nl < 0 ? sql : sql.substring(0, nl) + " ...";
    }

    /**
     * 把 SQLException 包装为统一的数据访问异常。
     */
    private static DataAccessException wrap(String action, String sql, SQLException e) {
        return new DataAccessException(action + "失败：" + e.getMessage(), sql, e);
    }
}
