package com.zfz.typhoon.perf;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * 压测专用假 JDBC 驱动：不连任何真实数据库，让连接池的"探活开销"可以离线测量。
 *
 * <h3>为什么需要它</h3>
 * {@code SimpleConnectionPool} 直接调 {@code DriverManager.getConnection(url, ...)}，
 * 无法注入替身连接，因此要测"探活成本"就只能连真库——而真库的往返时延既不可控、
 * 也做不到"把探活延迟设为固定值"来做对照实验。
 *
 * <p>做法是把一个 {@link Driver} 注册进 {@link DriverManager}，只认
 * {@link #URL} 这个私有协议。{@link #acceptsURL(String)} 对其它 URL 一律返回
 * false，因此同 JVM 里访问真实 MySQL 的测试完全不受影响。
 *
 * <p>{@link Connection#isValid(int)} 会被拦截并<b>休眠 {@link #PROBE_MICROS} 微秒</b>，
 * 用来模拟一次网络往返；同时计数，于是"借出侧探活几次、归还侧探活几次"都可被观测。
 *
 * @author 追风者项目组
 */
public final class FakeJdbcDriver implements Driver {

    /** 本驱动专属协议；连接池需要它才能走到这里 */
    public static final String URL = "jdbc:fake:probe";

    /** 每次 {@code isValid} 模拟的耗时（微秒），0 表示不模拟延迟 */
    public static volatile long probeMicros = 0L;

    /** {@code isValid} 被调用的累计次数 */
    public static final AtomicInteger VALIDATIONS = new AtomicInteger();

    /** 建立物理连接的累计次数（用于确认池没有反复重建连接） */
    public static final AtomicInteger CONNECTS = new AtomicInteger();

    static {
        try {
            DriverManager.registerDriver(new FakeJdbcDriver());
        } catch (SQLException e) {
            throw new IllegalStateException("注册假驱动失败", e);
        }
    }

    private FakeJdbcDriver() {
    }

    /** 确保类被加载并完成注册 */
    public static void ensureRegistered() {
        // 静态块完成注册，这里什么也不用做
    }

    /** 清零全部计数器，供每轮压测前复位 */
    public static void reset() {
        VALIDATIONS.set(0);
        CONNECTS.set(0);
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }
        CONNECTS.incrementAndGet();
        return fakeConnection();
    }

    @Override
    public boolean acceptsURL(String url) {
        return URL.equals(url);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        return 1;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    /**
     * 造一个只实现连接池真正会用到的几个方法的假连接。
     *
     * <p>连接池在借出/归还路径上会调用：{@code isValid} / {@code isClosed} /
     * {@code getAutoCommit} / {@code setAutoCommit} / {@code rollback} / {@code close}。
     * 其余方法一律返回类型默认值，真被调用到说明实现变了，届时测试会更容易暴露出来。
     *
     * @return 假连接
     */
    private static Connection fakeConnection() {
        return (Connection) Proxy.newProxyInstance(
                FakeJdbcDriver.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new InvocationHandler() {
                    private boolean autoCommit = true;

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String name = method.getName();
                        if ("isValid".equals(name)) {
                            VALIDATIONS.incrementAndGet();
                            long micros = probeMicros;
                            if (micros > 0L) {
                                // 模拟一次探活的网络往返
                                Thread.sleep(micros / 1000L, (int) ((micros % 1000L) * 1000L));
                            }
                            return Boolean.TRUE;
                        }
                        if ("isClosed".equals(name)) {
                            return Boolean.FALSE;
                        }
                        if ("getAutoCommit".equals(name)) {
                            return Boolean.valueOf(autoCommit);
                        }
                        if ("setAutoCommit".equals(name)) {
                            autoCommit = ((Boolean) args[0]).booleanValue();
                            return null;
                        }
                        if ("close".equals(name) || "rollback".equals(name)) {
                            return null;
                        }
                        if ("toString".equals(name)) {
                            return "FakeConnection";
                        }
                        if ("hashCode".equals(name)) {
                            return Integer.valueOf(System.identityHashCode(proxy));
                        }
                        if ("equals".equals(name)) {
                            return Boolean.valueOf(proxy == args[0]);
                        }
                        return defaultValue(method.getReturnType());
                    }
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == void.class) {
            return null;
        }
        if (type == long.class) {
            return Long.valueOf(0L);
        }
        if (type == double.class) {
            return Double.valueOf(0D);
        }
        if (type == float.class) {
            return Float.valueOf(0F);
        }
        if (type == char.class) {
            return Character.valueOf('\0');
        }
        if (type == byte.class) {
            return Byte.valueOf((byte) 0);
        }
        if (type == short.class) {
            return Short.valueOf((short) 0);
        }
        return Integer.valueOf(0);
    }
}
