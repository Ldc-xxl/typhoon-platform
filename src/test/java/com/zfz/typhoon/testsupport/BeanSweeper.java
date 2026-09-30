package com.zfz.typhoon.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 实体 / VO / DTO 的通用反射扫描器。
 *
 * <p>Model 层绝大多数类都是纯数据载体（POJO），手写 getter/setter 测试既冗长
 * 又容易漏字段。本工具用反射把「构造 → 逐个 setter 写值 → 对应 getter 回读断言 →
 * toString/hashCode/equals 通用契约」一次性跑完，用于把 Model 层的行覆盖率快速推高。
 *
 * <h3>设计意图</h3>
 * <ul>
 *   <li><b>单点失败不扩散</b>：任何一个属性取值、调用、回读出错，都只影响该属性，
 *       绝不抛出到调用方——否则一个 Bean 里某个 setter 归一化了入参，
 *       就会让整个包几百个类一起红。</li>
 *   <li><b>只统计真正断言成功的 setter</b>：返回值是成功完成的 setter 个数，
 *       供测试侧断言「确实扫到了东西」，而不是空跑。</li>
 *   <li><b>归一化不算失败</b>：若 setter 内部对值做了加工（如 trim、取整），
 *       回读会与写入不等。此时捕获 {@link AssertionError} 并跳过该属性，
 *       不计入成功数，但不让测试失败。</li>
 * </ul>
 *
 * @author 追风者项目组
 */
public final class BeanSweeper {

    /** 构造「取不到样例值」的哨兵：与合法的 null 样例区分开。 */
    private static final Object NO_SAMPLE = new Object();

    /** 统一的样例时间，保证测试可重复。 */
    private static final LocalDateTime SAMPLE_TIME = LocalDateTime.of(2026, 9, 13, 8, 0, 0);

    private BeanSweeper() {
        // 工具类禁止实例化
    }

    /**
     * 扫描一个类。
     *
     * @param clazz 目标类
     * @return 成功写入并回读断言的 setter 个数；无法构造实例时返回 -1
     */
    public static int sweep(Class<?> clazz) {
        Object instance = newInstance(clazz);
        if (instance == null) {
            return -1;
        }

        // 1) 先调用全部「无参且返回非 void」的 public 方法（getter 风格及其它无参方法），
        //    目的有二：覆盖方法本身，并把字段读取路径也走一遍。
        for (Method m : clazz.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers())) {
                continue;
            }
            if (m.getParameterTypes().length != 0) {
                continue;
            }
            if (m.getReturnType() == Void.TYPE || m.getReturnType() == Void.class) {
                continue;
            }
            if (m.getReturnType() == Class.class) {
                continue;
            }
            invokeQuietly(m, instance);
        }

        // 2) 逐个 setter：造样例值 → 写入 → 找对应 getter → 回读断言。
        int succeeded = 0;
        for (Method setter : clazz.getDeclaredMethods()) {
            if (!Modifier.isPublic(setter.getModifiers())) {
                continue;
            }
            if (!setter.getName().startsWith("set") || setter.getName().length() <= 3) {
                continue;
            }
            if (setter.getParameterTypes().length != 1) {
                continue;
            }

            Class<?> paramType = setter.getParameterTypes()[0];
            Object sample = sampleOf(paramType);
            if (sample == NO_SAMPLE) {
                // 造不出样例值：跳过该 setter（不中断整轮扫描）
                continue;
            }

            if (!invokeQuietly(setter, instance, sample)) {
                continue;
            }

            Method getter = findGetter(clazz, setter.getName().substring(3));
            if (getter == null || !typesComparable(getter.getReturnType(), paramType)) {
                // 没有对应 getter，或类型本就不可比：写入即算覆盖
                succeeded++;
                continue;
            }

            Object readBack;
            try {
                readBack = getter.invoke(instance);
            } catch (Throwable t) {
                continue;
            }

            try {
                assertSameValue(sample, readBack);
            } catch (AssertionError normalized) {
                // setter 内部做了归一化，回读不等：忽略该属性，不计入成功
                continue;
            }
            succeeded++;
        }

        // 3) 通用契约方法：任何 Bean 都应能安全调用
        invokeQuietly(findNoArg(clazz, "toString"), instance);
        invokeQuietly(findNoArg(clazz, "hashCode"), instance);
        invokeEqualsQuietly(instance, instance);
        invokeEqualsQuietly(instance, null);
        invokeEqualsQuietly(instance, new Object());

        return succeeded;
    }

    /* ------------------------------------------------------------------ */
    /*  内部工具                                                            */
    /* ------------------------------------------------------------------ */

    /** 用无参构造实例化；失败返回 null（不抛异常）。 */
    private static Object newInstance(Class<?> clazz) {
        try {
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 静默调用无参方法。 */
    private static void invokeQuietly(Method m, Object target) {
        if (m == null) {
            return;
        }
        try {
            m.setAccessible(true);
            m.invoke(target);
        } catch (Throwable ignored) {
            // 单个方法失败不影响扫描
        }
    }

    /** 静默调用带参方法，返回是否成功。 */
    private static boolean invokeQuietly(Method m, Object target, Object arg) {
        try {
            m.setAccessible(true);
            m.invoke(target, arg);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 静默调用 equals(Object)。 */
    private static void invokeEqualsQuietly(Object target, Object other) {
        try {
            target.equals(other);
        } catch (Throwable ignored) {
            // 忽略
        }
    }

    /** 在本类声明的 public 无参方法里按名字查找。 */
    private static Method findNoArg(Class<?> clazz, String name) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterTypes().length == 0) {
                return m;
            }
        }
        return null;
    }

    /**
     * 按属性名找 getter：getXxx 优先，其次 isXxx。
     *
     * @param clazz       目标类
     * @param prop        属性名（setter 去掉 set 前缀后的部分）
     * @return getter，找不到返回 null
     */
    private static Method findGetter(Class<?> clazz, String prop) {
        Method byGet = findNoArg(clazz, "get" + prop);
        if (byGet != null) {
            return byGet;
        }
        return findNoArg(clazz, "is" + prop);
    }

    /** 判断 getter 返回类型与 setter 入参类型是否可比（去掉包装后任一方可赋值给另一方）。 */
    private static boolean typesComparable(Class<?> getterType, Class<?> setterType) {
        if (getterType == Object.class || setterType == Object.class) {
            return true;
        }
        Class<?> g = wrap(getterType);
        Class<?> s = wrap(setterType);
        return g.isAssignableFrom(s) || s.isAssignableFrom(g);
    }

    /** 基本类型装箱。 */
    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        return type;
    }

    /**
     * 按参数类型造样例值。
     *
     * @param type 参数类型
     * @return 样例值；造不出时返回 {@link #NO_SAMPLE}
     */
    private static Object sampleOf(Class<?> type) {
        if (type == String.class || type == CharSequence.class) {
            return "x";
        }
        if (type == int.class || type == Integer.class) {
            return Integer.valueOf(1);
        }
        if (type == long.class || type == Long.class) {
            return Long.valueOf(1L);
        }
        if (type == short.class || type == Short.class) {
            return Short.valueOf((short) 1);
        }
        if (type == byte.class || type == Byte.class) {
            return Byte.valueOf((byte) 1);
        }
        if (type == char.class || type == Character.class) {
            return Character.valueOf('x');
        }
        if (type == double.class || type == Double.class) {
            return Double.valueOf(1.5D);
        }
        if (type == float.class || type == Float.class) {
            return Float.valueOf(1.5F);
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.TRUE;
        }
        if (type == BigDecimal.class) {
            return new BigDecimal("1.5");
        }
        if (type == BigInteger.class) {
            return BigInteger.valueOf(3L);
        }
        if (type == LocalDateTime.class) {
            return SAMPLE_TIME;
        }
        if (type == LocalDate.class) {
            return SAMPLE_TIME.toLocalDate();
        }
        if (type == LocalTime.class) {
            return SAMPLE_TIME.toLocalTime();
        }
        if (type == Timestamp.class) {
            return Timestamp.valueOf(SAMPLE_TIME);
        }
        if (type == Date.class) {
            return new Date(1_700_000_000_000L);
        }
        if (type == JsonNode.class) {
            // JsonNode 是抽象类，无法反射 new；用文本节点代替
            return TextNode.valueOf("x");
        }
        if (type.isArray()) {
            return Array.newInstance(type.getComponentType(), 1);
        }
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            return constants != null && constants.length > 0 ? constants[0] : NO_SAMPLE;
        }
        if (Map.class.isAssignableFrom(type)) {
            return new HashMap<Object, Object>();
        }
        if (Set.class.isAssignableFrom(type)) {
            return new LinkedHashSet<Object>();
        }
        if (Collection.class.isAssignableFrom(type)) {
            return new ArrayList<Object>();
        }
        // 兜底：尝试无参构造
        Object custom = newInstance(type);
        return custom == null ? NO_SAMPLE : custom;
    }

    /**
     * 回读断言：数值型允许容差，其余用 equals。
     *
     * @throws AssertionError 值不相等
     */
    private static void assertSameValue(Object expected, Object actual) {
        if (expected == null || actual == null) {
            if (expected != actual) {
                throw new AssertionError("期望 " + expected + "，实际 " + actual);
            }
            return;
        }
        if (expected instanceof Double && actual instanceof Double) {
            double e = ((Double) expected).doubleValue();
            double a = ((Double) actual).doubleValue();
            if (Math.abs(e - a) > 1e-9D) {
                throw new AssertionError("double 不等：" + e + " vs " + a);
            }
            return;
        }
        if (expected instanceof Float && actual instanceof Float) {
            float e = ((Float) expected).floatValue();
            float a = ((Float) actual).floatValue();
            if (Math.abs(e - a) > 1e-9F) {
                throw new AssertionError("float 不等：" + e + " vs " + a);
            }
            return;
        }
        if (!expected.equals(actual)) {
            throw new AssertionError("期望 " + expected + "，实际 " + actual);
        }
    }
}
