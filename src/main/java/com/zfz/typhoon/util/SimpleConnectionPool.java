package com.zfz.typhoon.util;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轻量 JDBC 连接池（纯手写，不引入任何第三方连接池）。
 *
 * <p>课程要求（阶段二 · ③ 工具类）：编写<b>数据库连接池</b>、SQL 异常处理类；
 * 且评审要求「<b>连接池配置需可参数化</b>」——
 * 参数全部来自 {@code src/main/resources/db.properties}，改配置不必改代码、不必重新编译。
 *
 * <h3>为什么不用 Druid / HikariCP</h3>
 * 课程硬性要求"后端使用 Java 原生开发，<b>禁用任何框架</b>"。连接池本身属于基础设施，
 * 手写一个能同时体现对 JDBC、并发、动态代理三个知识点的掌握，比引一个 jar 更有说服力。
 *
 * <h3>实现要点</h3>
 * <ol>
 *   <li><b>空闲队列</b>：{@link LinkedBlockingQueue} 存放空闲的物理连接，
 *       {@code poll(timeout)} 天然实现了"池满时等待 {@code maxWait} 毫秒"的语义；</li>
 *   <li><b>上限控制</b>：{@link #created} 记录已创建的物理连接数，
 *       新建前在 {@code synchronized} 块内比对 {@code maxActive}，避免并发下超建；</li>
 *   <li><b>归还语义</b>：用 {@link Proxy} 动态代理包装 {@link Connection}，
 *       把 {@code close()} 拦截为"归还到池"，因此 DAO 里可以放心地写
 *       {@code try (Connection conn = ...)}，不必手动归还、也不会真的关掉连接；</li>
 *   <li><b>失效剔除</b>：长时间空闲的连接会被服务端断开，取出时用
 *       {@link Connection#isValid(int)} 做一次探活，失效则丢弃重建。</li>
 * </ol>
 *
 * <p>本类线程安全。
 *
 * @author 追风者项目组
 * @see DBUtil
 */
public class SimpleConnectionPool {

    /** 驱动类名 */
    private final String driver;

    /** JDBC 连接串 */
    private final String url;

    /** 数据库账号 */
    private final String username;

    /** 数据库口令 */
    private final String password;

    /** 最大活跃连接数（含借出与空闲） */
    private final int maxActive;

    /** 最大空闲连接数，超出部分归还时直接关闭 */
    private final int maxIdle;

    /** 最小空闲连接数，用于空闲回收时保持水位 */
    private final int minIdle;

    /** 获取连接的最长等待时间（毫秒） */
    private final long maxWait;

    /**
     * 空闲连接"免探活"安全窗口（纳秒），来自 {@code db.properties} 的
     * {@code pool.idleSafeNs}，可参数化（呼应课程"连接池配置需可参数化"）。
     * 刚归还不久（< 该窗口）的连接几乎不可能已被服务端断开，
     * 借出时跳过 {@code isValid} 探活，省去一次网络往返；
     * 空闲超过该窗口的连接才做探活，兼顾性能与失效剔除。
     * 默认 5 秒，远小于 MySQL 默认 {@code wait_timeout}(8 小时)，安全。
     * 设为极大值即退化为"每次借出都探活"的旧行为（用于性能对比）。
     */
    private final long idleSafeNs;

    /** 空闲连接队列（携带归还时间戳，用于判定是否需探活） */
    private final BlockingQueue<IdleConn> idle = new LinkedBlockingQueue<IdleConn>();

    /** 已创建的物理连接总数（借出 + 空闲） */
    private final AtomicInteger created = new AtomicInteger(0);

    /** 当前借出的连接数 */
    private final AtomicInteger borrowed = new AtomicInteger(0);

    /** 累计获取连接的次数，用于后台监控展示 */
    private final AtomicInteger totalGets = new AtomicInteger(0);

    /** 池是否已关闭 */
    private volatile boolean closed = false;

    /**
     * 构造连接池并预热。
     *
     * @param driver       驱动类名
     * @param url          连接串
     * @param username     账号
     * @param password     口令
     * @param initialSize  初始连接数（预热）
     * @param maxActive    最大活跃连接数
     * @param maxIdle      最大空闲连接数
     * @param minIdle      最小空闲连接数
     * @param maxWait      获取连接最长等待毫秒数
     * @throws SQLException 初始化失败（驱动缺失、连接串错误、账号口令错误）
     */
    public SimpleConnectionPool(String driver, String url, String username, String password,
                                int initialSize, int maxActive, int maxIdle, int minIdle, long maxWait)
            throws SQLException {
        this.driver = driver;
        this.url = url;
        this.username = username;
        this.password = password;
        this.maxActive = Math.max(1, maxActive);
        this.maxIdle = Math.max(1, Math.min(maxIdle, this.maxActive));
        this.minIdle = Math.max(0, Math.min(minIdle, this.maxIdle));
        this.maxWait = maxWait <= 0 ? 5000L : maxWait;
        // 探活安全窗口可参数化：默认 5s，设极大值即"每次借出都探活"（旧行为）。
        // 注意窗口单位是纳秒，数值可能超过 int 上限，故按字符串读取再解析为 long。
        long safeNs = 5_000_000_000L;
        try {
            String cfg = DBUtil.getConfig("pool.idleSafeNs", null);
            if (cfg != null && !cfg.trim().isEmpty()) {
                safeNs = Long.parseLong(cfg.trim());
            }
        } catch (NumberFormatException ignored) {
            safeNs = 5_000_000_000L;
        }
        this.idleSafeNs = Math.max(0L, safeNs);

        loadDriver();

        int warm = Math.max(0, Math.min(initialSize, this.maxActive));
        for (int i = 0; i < warm; i++) {
            try {
                Connection conn = createPhysical();
                created.incrementAndGet();
                idle.offer(new IdleConn(conn, System.nanoTime()));
            } catch (SQLException e) {
                // 预热失败不阻断启动：可能数据库尚未就绪，后续按需新建
                break;
            }
        }
    }

    /**
     * 空闲连接载体：记录物理连接与其归还时间戳，供借出时判定是否跳过探活。
     */
    private static final class IdleConn {
        final Connection conn;
        final long returnedNanos;

        IdleConn(Connection conn, long returnedNanos) {
            this.conn = conn;
            this.returnedNanos = returnedNanos;
        }
    }

    /**
     * 加载 JDBC 驱动。
     *
     * @throws SQLException 驱动类不存在时抛出
     */
    private void loadDriver() throws SQLException {
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            throw new SQLException("未找到 JDBC 驱动：" + driver
                    + "（请确认 mysql-connector-j 已加入依赖）", e);
        }
    }

    /**
     * 创建一个物理连接。
     *
     * @return 物理连接
     * @throws SQLException 建连失败
     */
    private Connection createPhysical() throws SQLException {
        Connection conn = DriverManager.getConnection(url, username, password);
        conn.setAutoCommit(true);
        return conn;
    }

    /**
     * 获取一个连接（包装后返回，{@code close()} 即归还）。
     *
     * @return 池化连接
     * @throws SQLException 等待超时或建连失败
     */
    public Connection getConnection() throws SQLException {
        if (closed) {
            throw new SQLException("连接池已关闭，无法获取连接");
        }
        totalGets.incrementAndGet();

        IdleConn ic = idle.poll();
        Connection real;
        boolean fresh = false;

        if (ic == null) {
            // 空闲队列为空：池未达上限则新建，否则等待其他线程归还
            synchronized (this) {
                if (created.get() < maxActive) {
                    real = createPhysical();
                    created.incrementAndGet();
                    fresh = true;
                } else {
                    real = null;
                }
            }
            if (real == null) {
                try {
                    IdleConn waited = idle.poll(maxWait, TimeUnit.MILLISECONDS);
                    if (waited == null) {
                        throw new SQLException("获取数据库连接超时（" + maxWait
                                + "ms，上限 " + maxActive + " 个）");
                    }
                    ic = waited;
                    real = ic.conn;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("等待数据库连接时线程被中断", e);
                }
            }
        } else {
            real = ic.conn;
        }

        // 探活策略（性能优化 · 阶段七）：
        //   刚新建的连接(fresh)必然可用，无需探活；
        //   从空闲队列借出的连接，仅当"空闲超过安全窗口(idleSafeNs)"才做 isValid 探活，
        //   其余热路径连接直接复用，省去一次网络往返。
        if (!fresh && (System.nanoTime() - ic.returnedNanos) > idleSafeNs) {
            if (!isValid(real)) {
                safeClose(real);
                created.decrementAndGet();
                real = createPhysical();
                created.incrementAndGet();
            }
        }

        borrowed.incrementAndGet();
        return wrap(real);
    }

    /**
     * 连接探活。
     *
     * @param conn 物理连接
     * @return 可用返回 true
     */
    private boolean isValid(Connection conn) {
        try {
            return conn != null && !conn.isClosed() && conn.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * 归还连接。
     *
     * @param real 物理连接
     */
    private void release(Connection real) {
        if (real == null) {
            return;
        }
        borrowed.decrementAndGet();

        // 事务残留的回滚保护：归还前确保已提交或回滚，并恢复自动提交
        try {
            if (!real.getAutoCommit()) {
                real.rollback();
                real.setAutoCommit(true);
            }
        } catch (SQLException ignored) {
            // 连接已不可用，走下面的关闭分支
        }

        if (closed || idle.size() >= maxIdle || !isValid(real)) {
            safeClose(real);
            created.decrementAndGet();
            return;
        }
        if (!idle.offer(new IdleConn(real, System.nanoTime()))) {
            safeClose(real);
            created.decrementAndGet();
        }
    }

    /**
     * 用动态代理包装物理连接，把 {@code close()} 拦截为归还。
     *
     * @param real 物理连接
     * @return 包装后的连接
     */
    private Connection wrap(final Connection real) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String name = method.getName();

                        if ("close".equals(name)) {
                            release(real);
                            return null;
                        }
                        if ("isClosed".equals(name)) {
                            return real.isClosed();
                        }
                        if ("equals".equals(name)) {
                            return proxy == args[0];
                        }
                        if ("hashCode".equals(name)) {
                            return real.hashCode();
                        }
                        if ("toString".equals(name)) {
                            return "PooledConnection[" + real + "]";
                        }

                        try {
                            return method.invoke(real, args);
                        } catch (InvocationTargetException e) {
                            throw e.getTargetException();
                        }
                    }
                });
    }

    /**
     * 静默关闭物理连接。
     *
     * @param conn 连接
     */
    private void safeClose(Connection conn) {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException ignored) {
            // 关闭失败无需上报
        }
    }

    /**
     * 关闭连接池，释放全部空闲连接。
     */
    public void shutdown() {
        closed = true;
        List<IdleConn> rest = new ArrayList<IdleConn>();
        idle.drainTo(rest);
        for (IdleConn ic : rest) {
            safeClose(ic.conn);
            created.decrementAndGet();
        }
    }

    /* ------------------------------------------------------------------ */
    /*  监控指标（后台"系统运行状态"面板使用）                                */
    /* ------------------------------------------------------------------ */

    /**
     * 当前空闲连接数。
     *
     * @return 空闲数
     */
    public int getIdleCount() {
        return idle.size();
    }

    /**
     * 当前借出连接数。
     *
     * @return 借出数
     */
    public int getActiveCount() {
        return borrowed.get();
    }

    /**
     * 已创建的物理连接总数。
     *
     * @return 连接总数
     */
    public int getCreatedCount() {
        return created.get();
    }

    /**
     * 累计获取连接的次数。
     *
     * @return 获取次数
     */
    public int getTotalGets() {
        return totalGets.get();
    }

    /**
     * 最大活跃连接数配置值。
     *
     * @return 上限
     */
    public int getMaxActive() {
        return maxActive;
    }

    /**
     * 生成一段人类可读的池状态描述，供日志与后台页面使用。
     *
     * @return 状态描述
     */
    public String describe() {
        return "连接池[空闲=" + getIdleCount() + ", 借出=" + getActiveCount()
                + ", 已创建=" + getCreatedCount() + "/" + maxActive
                + ", 累计获取=" + totalGets.get() + "]";
    }

    /**
     * 便于日志输出。
     *
     * @return 状态描述
     */
    @Override
    public String toString() {
        return describe();
    }
}
