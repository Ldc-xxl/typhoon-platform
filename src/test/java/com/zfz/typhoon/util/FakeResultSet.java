package com.zfz.typhoon.util;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link ResultSet} 测试替身（基于 {@link Proxy} 的动态代理实现）。
 *
 * <h3>为什么不用 Mockito</h3>
 * 本工程默认的 Mockito 5 使用 inline mock maker，它需要<b>改写既有类</b>。
 * {@code ResultSet} 位于 JDK 的 {@code java.sql} 模块中，模块封装不允许外部改写，
 * 于是 Mockito 会抛 {@code MockitoException: Could not modify all classes
 * [interface java.sql.Wrapper, interface java.lang.AutoCloseable, interface java.sql.ResultSet]}。
 * 绕开它的常规做法是给 JVM 加 {@code --add-opens java.sql/java.sql=ALL-UNNAMED}，
 * 但那个开关在 JDK 8 上不存在，会让"课程机房里 JDK 8 跑测试"直接失败。
 *
 * <p>因此这里改用不依赖字节码增强的动态代理：实现一个 {@code ResultSet} 接口的
 * 运行时子类，按"列名 → 值"查表返回。它同时具备一个额外好处 ——
 * <b>如实模拟了驱动的 NULL 语义</b>：某列取值缺失时，原始类型 getter 返回 0，
 * 而 {@code wasNull()} 变为 true。这正是 {@link RsUtil} 全部防御逻辑所针对的场景，
 * 用 Mockito 逐个桩定反而更容易把这条契约写歪。
 *
 * <h3>用法</h3>
 * <pre>{@code
 * ResultSet rs = FakeResultSet.create().with("typhoon_id", 13L).rs();
 * ResultSet nullRs = FakeResultSet.create().rs();          // 任何列都是 NULL
 * ResultSet badRs = FakeResultSet.create()
 *         .failing("missing_col", new SQLException("no such column")).rs();
 * }</pre>
 *
 * @author 追风者项目组
 * @see RsUtilTest
 */
final class FakeResultSet {

    /** 列名 → 值；键不存在即代表该列为 SQL NULL */
    private final Map<String, Object> columns = new HashMap<String, Object>();

    /** 列名 → 待抛异常，用于模拟"列不存在 / 读取失败" */
    private final Map<String, SQLException> failures = new HashMap<String, SQLException>();

    /** 最近一次取值是否为 NULL，对应驱动实现的 {@code wasNull()} */
    private boolean lastWasNull;

    private final ResultSet proxy;

    private FakeResultSet() {
        this.proxy = (ResultSet) Proxy.newProxyInstance(
                FakeResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object self, Method method, Object[] args) throws Throwable {
                        return dispatch(method, args, self);
                    }
                });
    }

    /**
     * 创建替身。
     *
     * @return 尚未设定任何列的替身（此时所有列都读作 NULL）
     */
    static FakeResultSet create() {
        return new FakeResultSet();
    }

    /**
     * 设定某一列的值。
     *
     * @param column 列名
     * @param value  值；传 null 等价于"该列为 NULL"
     * @return this，便于链式书写
     */
    FakeResultSet with(String column, Object value) {
        columns.put(column, value);
        return this;
    }

    /**
     * 让读取某一列时抛出异常。
     *
     * @param column 列名
     * @param e      要抛出的异常
     * @return this
     */
    FakeResultSet failing(String column, SQLException e) {
        failures.put(column, e);
        return this;
    }

    /**
     * @return 可供被测代码使用的 {@link ResultSet}
     */
    ResultSet rs() {
        return proxy;
    }

    /**
     * 把接口方法调用翻译成对 {@link #columns} 的查表动作。
     *
     * @param method 被调用的接口方法
     * @param args   实参
     * @param self   代理对象自身（用于 equals / hashCode）
     * @return 方法的返回值
     * @throws SQLException 该列被设定为失败时抛出
     */
    private Object dispatch(Method method, Object[] args, Object self) throws SQLException {
        String name = method.getName();

        // Object 上的三个方法由代理自行承担，不去查列
        if ("wasNull".equals(name)) {
            return Boolean.valueOf(lastWasNull);
        }
        if ("toString".equals(name)) {
            return "FakeResultSet" + columns;
        }
        if ("hashCode".equals(name)) {
            return Integer.valueOf(System.identityHashCode(self));
        }
        if ("equals".equals(name)) {
            return Boolean.valueOf(self == args[0]);
        }

        String column = args == null || args.length == 0 ? "" : String.valueOf(args[0]);

        SQLException failure = failures.get(column);
        if (failure != null) {
            lastWasNull = true;
            throw failure;
        }

        Object value = columns.get(column);
        // 与真实驱动一致：先让 wasNull() 反映本次读取，再返回值
        lastWasNull = value == null;

        if ("getString".equals(name) || "getBigDecimal".equals(name)
                || "getTimestamp".equals(name) || "getObject".equals(name)) {
            // 引用类型 getter：NULL 直接就是 null
            return value;
        }
        if ("getLong".equals(name)) {
            return Long.valueOf(value == null ? 0L : ((Number) value).longValue());
        }
        if ("getInt".equals(name)) {
            return Integer.valueOf(value == null ? 0 : ((Number) value).intValue());
        }
        if ("getDouble".equals(name)) {
            return Double.valueOf(value == null ? 0D : ((Number) value).doubleValue());
        }
        if ("getBoolean".equals(name)) {
            return Boolean.valueOf(value != null && ((Number) value).intValue() != 0);
        }

        throw new UnsupportedOperationException(
                "FakeResultSet 未实现的方法：" + name + "，如需使用请在此补充");
    }
}
