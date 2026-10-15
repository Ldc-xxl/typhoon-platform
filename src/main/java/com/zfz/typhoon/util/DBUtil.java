package com.zfz.typhoon.util;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 数据库访问门面（连接获取 · 事务 · 资源释放 · 配置读取）。
 *
 * <p>课程要求（阶段二 · ③ 工具类）：数据库连接池 + SQL 异常处理类；
 * 评审要求「配置需可参数化」。
 *
 * <h3>职责边界</h3>
 * <ul>
 *   <li>本类只负责"拿到连接 / 管好事务 / 关掉资源"，<b>不含任何 SQL</b>；</li>
 *   <li>SQL 一律写在 {@code dao.impl} 包下的实现类里，且必须使用
 *       {@link java.sql.PreparedStatement}（防 SQL 注入）；</li>
 *   <li>配置从 {@code db.properties} 读取，支持通过系统属性
 *       {@code -Djdbc.url=...} 覆盖，便于在不同机器上免改包运行。</li>
 * </ul>
 *
 * <h3>事务用法</h3>
 * <p><b>推荐用 {@link #runInTransaction(TxWork)}</b>——它把
 * 开启 / 提交 / 回滚 / 归还在同一个方法里成对写好，调用方不可能漏掉
 * {@code endTransaction()}，也不可能忘记在异常分支回滚：
 * <pre>{@code
 * return DBUtil.runInTransaction(new DBUtil.TxWork<Integer>() {
 *     public Integer run() {
 *         long id = orderDao.insert(order);
 *         detailDao.insert(detail);
 *         return (int) id;
 *     }
 * });
 * }</pre>
 *
 * <p>需要更细粒度控制时也可以用下层的四件套（语义与 {@code runInTransaction}
 * 完全一致，只是把顺序交给调用方）：
 * <pre>{@code
 * DBUtil.beginTransaction();
 * try {
 *     orderDao.insert(order);
 *     detailDao.insert(detail);
 *     DBUtil.commit();
 * } catch (RuntimeException e) {
 *     DBUtil.rollback();
 *     throw e;
 * } finally {
 *     DBUtil.endTransaction();
 * }
 * }</pre>
 *
 * <p>实现细节：事务连接存放在 {@link ThreadLocal} 中，因此
 * <b>事务期间调用 {@link #getConnection()} 会拿到同一个连接</b>，
 * 且 DAO 里 {@code try (Connection c = DBUtil.getConnection())} 的
 * {@code close()} 被改写为"空操作"，不会提前把连接还回池子。
 *
 * <h3>为什么需要 {@link TxMode#DIRECT}</h3>
 * Service 层的单元测试用替身 DAO（Mockito mock）驱动，全程不产生真实 SQL，
 * 却仍要调用被事务包住的业务方法。若此时真的去连接池取连接开启事务，
 * 单元测试就变成了"必须有数据库才能跑"的集成测试。因此本类提供一个
 * <b>显式</b>的执行模式开关：{@link TxMode#DIRECT} 下事务退化为空操作。
 *
 * <p><b>它不是"静默降级"</b>——只有代码显式调用
 * {@link #setTxMode(TxMode)} 才会切换，且默认值永远是 {@link TxMode#JDBC}，
 * 首次切到 {@code DIRECT} 时会打一条 WARNING 日志。
 *
 * @author 追风者项目组
 * @see SimpleConnectionPool
 * @see DataAccessException
 */
public final class DBUtil {

    /** 配置文件路径（classpath 根下） */
    private static final String CONFIG_FILE = "db.properties";

    private static final Logger LOG = Logger.getLogger(DBUtil.class.getName());

    /** 配置项 */
    private static final Properties PROPS = new Properties();

    /** 连接池实例；初始化失败时为 null */
    private static volatile SimpleConnectionPool pool;

    /** 初始化失败原因，供健康检查接口返回 */
    private static volatile String initError;

    /** 当前线程的事务连接 */
    private static final ThreadLocal<Connection> TX_CONNECTION = new ThreadLocal<Connection>();

    /** 是否打印 SQL 与耗时 */
    private static boolean showSql = false;

    /**
     * 事务执行模式。默认 {@link TxMode#JDBC}；仅单元测试会切成
     * {@link TxMode#DIRECT}，详见类注释「为什么需要 DIRECT」。
     */
    private static volatile TxMode txMode = TxMode.JDBC;

    /** {@code DIRECT} 模式下只需提醒一次，避免每个用例刷屏 */
    private static volatile boolean directModeWarned = false;

    /**
     * 事务执行模式。
     */
    public enum TxMode {
        /** 真实事务：从连接池取连接、关闭自动提交，由数据库保证原子性 */
        JDBC,
        /**
         * 直通模式：{@link #beginTransaction()} 等退化为空操作。
         * <p><b>仅供单元测试使用</b>——那些用例用替身 DAO，不产生真实 SQL，
         * 不需要（也无法）建立数据库事务。生产代码不得切换到此模式。
         */
        DIRECT
    }

    /**
     * 事务工作单元：{@link #runInTransaction(TxWork)} 的入参。
     *
     * <p>{@code run()} 内抛出的任何运行时异常都会触发回滚并原样向上抛出，
     * 调用方因此可以放心地"throw 即回滚"。
     *
     * @param <T> 返回值类型
     */
    public interface TxWork<T> {
        /**
         * 在事务中执行的工作。
         *
         * @return 工作结果
         */
        T run();
    }

    private DBUtil() {
        // 工具类禁止实例化
    }

    static {
        loadConfig();
        initPool();
    }

    /* ------------------------------------------------------------------ */
    /*  初始化                                                              */
    /* ------------------------------------------------------------------ */

    /**
     * 从 classpath 读取 db.properties。
     */
    private static void loadConfig() {
        InputStream in = null;
        try {
            in = DBUtil.class.getClassLoader().getResourceAsStream(CONFIG_FILE);
            if (in == null) {
                initError = "未找到 " + CONFIG_FILE + "（应位于 src/main/resources 下）";
                return;
            }
            PROPS.load(new java.io.InputStreamReader(in, "UTF-8"));
            showSql = "true".equalsIgnoreCase(getConfig("sql.show", "false"));
        } catch (IOException e) {
            initError = "读取 " + CONFIG_FILE + " 失败：" + e.getMessage();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // 忽略
                }
            }
        }
    }

    /**
     * 初始化连接池。
     */
    private static void initPool() {
        if (initError != null) {
            return;
        }
        try {
            pool = new SimpleConnectionPool(
                    getConfig("jdbc.driver", "com.mysql.cj.jdbc.Driver"),
                    getConfig("jdbc.url", "jdbc:mysql://localhost:3306/typhoon_monitor"),
                    getConfig("jdbc.username", "root"),
                    getConfig("jdbc.password", ""),
                    getIntConfig("pool.initialSize", 5),
                    getIntConfig("pool.maxActive", 20),
                    getIntConfig("pool.maxIdle", 10),
                    getIntConfig("pool.minIdle", 5),
                    getIntConfig("pool.maxWait", 5000));
        } catch (Exception e) {
            initError = "连接池初始化失败：" + e.getMessage();
        }
    }

    /* ------------------------------------------------------------------ */
    /*  配置读取                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * 读取配置项。优先取系统属性，便于 {@code -Djdbc.url=...} 临时覆盖。
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public static String getConfig(String key, String defaultValue) {
        String sys = System.getProperty(key);
        if (sys != null && !sys.trim().isEmpty()) {
            return sys.trim();
        }
        String v = PROPS.getProperty(key);
        return v == null ? defaultValue : v.trim();
    }

    /**
     * 读取整数配置项。
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public static int getIntConfig(String key, int defaultValue) {
        String v = getConfig(key, null);
        if (v == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /* ------------------------------------------------------------------ */
    /*  连接与事务                                                          */
    /* ------------------------------------------------------------------ */

    /**
     * 获取数据库连接。
     *
     * <p>若当前线程处于事务中，返回同一个事务连接（其 {@code close()} 为空操作）；
     * 否则从连接池取，{@code close()} 即归还。
     *
     * @return 数据库连接
     * @throws DataAccessException 连接池不可用或获取超时
     */
    public static Connection getConnection() {
        Connection tx = TX_CONNECTION.get();
        if (tx != null) {
            return nonClosingProxy(tx);
        }
        SimpleConnectionPool p = pool;
        if (p == null) {
            throw new DataAccessException(
                    initError == null ? "数据库连接池不可用" : initError, null);
        }
        try {
            return p.getConnection();
        } catch (SQLException e) {
            throw new DataAccessException("获取数据库连接失败：" + e.getMessage(), e);
        }
    }

    /**
     * 开启事务。多次调用只生效一次（不支持嵌套）。
     *
     * <p>处于 {@link TxMode#DIRECT} 时不取连接、不做任何事，
     * 后续的 {@code commit / rollback / endTransaction} 因为读不到事务连接
     * 也会自动成为空操作。
     *
     * @throws DataAccessException 获取连接或关闭自动提交失败
     */
    public static void beginTransaction() {
        if (TX_CONNECTION.get() != null) {
            return;                      // 已在事务中，复用
        }
        if (txMode == TxMode.DIRECT) {
            if (!directModeWarned) {
                directModeWarned = true;
                LOG.log(Level.WARNING,
                        "事务处于 DIRECT 模式：beginTransaction 等已退化为空操作。"
                                + "该模式只应在使用替身 DAO 的单元测试中启用。");
            }
            return;
        }
        SimpleConnectionPool p = pool;
        if (p == null) {
            throw new DataAccessException(
                    initError == null ? "数据库连接池不可用" : initError, null);
        }
        try {
            Connection conn = p.getConnection();
            conn.setAutoCommit(false);
            TX_CONNECTION.set(conn);
        } catch (SQLException e) {
            throw new DataAccessException("开启事务失败：" + e.getMessage(), e);
        }
    }

    /**
     * 提交事务。
     *
     * @throws DataAccessException 提交失败
     */
    public static void commit() {
        Connection conn = TX_CONNECTION.get();
        if (conn == null) {
            return;
        }
        try {
            conn.commit();
        } catch (SQLException e) {
            throw new DataAccessException("提交事务失败：" + e.getMessage(), e);
        }
    }

    /**
     * 回滚事务。回滚失败不抛出，避免掩盖原始业务异常。
     */
    public static void rollback() {
        Connection conn = TX_CONNECTION.get();
        if (conn == null) {
            return;
        }
        try {
            conn.rollback();
        } catch (SQLException ignored) {
            // 回滚失败无法补救，忽略以保留原始异常
        }
    }

    /**
     * 结束事务：把连接恢复到自动提交并归还池中。
     */
    public static void endTransaction() {
        Connection conn = TX_CONNECTION.get();
        if (conn == null) {
            return;
        }
        TX_CONNECTION.remove();
        try {
            conn.setAutoCommit(true);
        } catch (SQLException ignored) {
            // 忽略
        }
        try {
            conn.close();                // 归还给连接池
        } catch (SQLException ignored) {
            // 忽略
        }
    }

    /**
     * 判断当前线程是否处于事务中。
     *
     * @return 在事务中返回 true
     */
    public static boolean inTransaction() {
        return TX_CONNECTION.get() != null;
    }

    /**
     * 在一个事务里执行工作单元：开启 → 执行 → 提交；出异常则回滚并原样抛出，
     * 无论成败最后都会结束事务（把连接归还池中）。
     *
     * <p>这是本类<b>推荐的事务入口</b>。之所以不是只提供
     * {@code begin/commit/rollback/endTransaction} 四件套：那四件套要求调用方
     * 在 {@code finally} 里收尾、在 {@code catch} 里回滚，任何一处漏写都会
     * 变成"看起来有事务、实际没生效"的隐性缺陷——本项目早先正是如此，
     * 整仓 {@code src/main} 里 {@code beginTransaction()} 一个调用点都没有。
     *
     * <p>注意：只回滚 {@link RuntimeException}。Java 的受检异常在本项目里
     * 统一被包装成 {@link BusinessException} / {@link DataAccessException}
     * （均为运行时异常），因此这一约束与全项目的异常契约一致。
     *
     * @param work 工作单元
     * @param <T>  返回值类型
     * @return 工作单元的返回值
     * @throws RuntimeException 工作单元抛出异常时，回滚后原样抛出
     */
    public static <T> T runInTransaction(TxWork<T> work) {
        beginTransaction();
        try {
            T result = work.run();
            commit();
            return result;
        } catch (RuntimeException e) {
            rollback();
            throw e;
        } finally {
            endTransaction();
        }
    }

    /**
     * 读取当前事务执行模式。
     *
     * @return 事务模式
     */
    public static TxMode getTxMode() {
        return txMode;
    }

    /**
     * 切换事务执行模式。
     *
     * <p><b>仅供单元测试</b>（见类注释「为什么需要 DIRECT」）。测试用例应在
     * {@code @BeforeEach} 切到 {@link TxMode#DIRECT}、在 {@code @AfterEach}
     * 切回 {@link TxMode#JDBC}，避免污染同一 JVM 里的其它用例。
     *
     * @param mode 目标模式；传 {@code null} 视为 {@link TxMode#JDBC}
     */
    public static void setTxMode(TxMode mode) {
        txMode = mode == null ? TxMode.JDBC : mode;
    }

    /**
     * 包装事务连接：把 {@code close()} 改为空操作，避免 DAO 的
     * try-with-resources 提前归还事务连接。
     *
     * @param real 事务连接
     * @return 包装后的连接
     */
    private static Connection nonClosingProxy(final Connection real) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String name = method.getName();
                        if ("close".equals(name)) {
                            return null;                  // 事务期间不关闭
                        }
                        if ("equals".equals(name)) {
                            return proxy == args[0];
                        }
                        if ("hashCode".equals(name)) {
                            return real.hashCode();
                        }
                        if ("toString".equals(name)) {
                            return "TxConnection[" + real + "]";
                        }
                        try {
                            return method.invoke(real, args);
                        } catch (InvocationTargetException e) {
                            throw e.getTargetException();
                        }
                    }
                });
    }

    /* ------------------------------------------------------------------ */
    /*  资源释放                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * 静默关闭 JDBC 资源（顺序：ResultSet → Statement → Connection）。
     *
     * @param rs  结果集，可为 null
     * @param st  语句，可为 null
     * @param conn 连接，可为 null
     */
    public static void close(ResultSet rs, Statement st, Connection conn) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException ignored) {
                // 忽略
            }
        }
        if (st != null) {
            try {
                st.close();
            } catch (SQLException ignored) {
                // 忽略
            }
        }
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {
                // 忽略
            }
        }
    }

    /**
     * 静默关闭语句与结果集（连接由调用方管理）。
     *
     * @param rs 结果集，可为 null
     * @param st 语句，可为 null
     */
    public static void close(ResultSet rs, Statement st) {
        close(rs, st, null);
    }

    /**
     * 静默关闭连接。
     *
     * @param conn 连接，可为 null
     */
    public static void close(Connection conn) {
        close(null, null, conn);
    }

    /* ------------------------------------------------------------------ */
    /*  监控与诊断                                                          */
    /* ------------------------------------------------------------------ */

    /**
     * 获取连接池实例（供后台"系统运行状态"面板展示监控指标）。
     *
     * @return 连接池；未初始化成功时返回 null
     */
    public static SimpleConnectionPool getPool() {
        return pool;
    }

    /**
     * 数据库是否可用（配置加载 + 连接池初始化均成功）。
     *
     * @return 可用返回 true
     */
    public static boolean isAvailable() {
        return pool != null;
    }

    /**
     * 初始化失败原因。
     *
     * @return 失败原因；成功时返回 null
     */
    public static String getInitError() {
        return initError;
    }

    /**
     * 是否需要打印 SQL。
     *
     * @return 需要返回 true
     */
    public static boolean isShowSql() {
        return showSql;
    }

    /**
     * 打印 SQL 与参数（仅开发期，由 {@code sql.show=true} 控制）。
     *
     * @param sql    SQL 语句
     * @param params 参数数组
     */
    public static void logSql(String sql, Object[] params) {
        if (!showSql) {
            return;
        }
        StringBuilder sb = new StringBuilder("[SQL] ").append(sql);
        if (params != null && params.length > 0) {
            sb.append("  params=");
            for (int i = 0; i < params.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(params[i]);
            }
        }
        System.out.println(sb.toString());
    }

    /**
     * 启动自检：尝试取一次连接，返回可读结论。
     *
     * <p>用于后台"数据源监控"面板与部署验收（{@code /api/health}）。
     *
     * @return 自检结论
     */
    public static String selfCheck() {
        if (pool == null) {
            return "FAILED: " + (initError == null ? "连接池未初始化" : initError);
        }
        Connection conn = null;
        try {
            conn = getConnection();
            return "OK: " + pool.describe();
        } catch (RuntimeException e) {
            return "FAILED: " + e.getMessage();
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException ignored) {
                    // 忽略
                }
            }
        }
    }

    /**
     * 关闭连接池（由 {@code ContextListener} 在应用卸载时调用），
     * 避免 Tomcat 热部署时遗留连接造成内存泄漏。
     */
    public static void shutdown() {
        if (pool != null) {
            pool.shutdown();
            pool = null;
        }
    }
}
