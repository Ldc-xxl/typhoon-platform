package com.zfz.typhoon.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DateUtil} 单元测试。
 *
 * <p>被测对象是「前后端日期字符串口径」的执行者：全站时间轴、轨迹时刻、
 * 采集时间都靠它输出 {@code yyyy-MM-dd HH:mm:ss}。一旦这里漂成 ISO 格式，
 * 前端会自动把它当"非法时间"处理，页面时间轴整体错位，因此格式断言写得比较死。
 */
@DisplayName("DateUtil · 日期时间工具")
class DateUtilTest {

    @Nested
    @DisplayName("format · 格式化输出")
    class Format {

        @Test
        @DisplayName("LocalDateTime 输出 yyyy-MM-dd HH:mm:ss")
        void formatsDateTime() {
            assertEquals("2026-09-13 08:30:05",
                    DateUtil.format(LocalDateTime.of(2026, 9, 13, 8, 30, 5)));
        }

        @Test
        @DisplayName("LocalDate 输出 yyyy-MM-dd")
        void formatsDate() {
            assertEquals("2026-09-13", DateUtil.format(LocalDate.of(2026, 9, 13)));
        }

        @Test
        @DisplayName("月/日/时/分/秒为个位数时补前导零")
        void padsSingleDigits() {
            assertEquals("2026-01-02 03:04:05",
                    DateUtil.format(LocalDateTime.of(2026, 1, 2, 3, 4, 5)));
        }

        @Test
        @DisplayName("入参为 null 时返回 null 而不是抛异常")
        void nullSafe() {
            assertNull(DateUtil.format((LocalDateTime) null));
            assertNull(DateUtil.format((LocalDate) null));
        }
    }

    @Nested
    @DisplayName("parse · 严格解析")
    class Parse {

        @Test
        @DisplayName("标准格式可解析")
        void parsesStandard() {
            assertEquals(LocalDateTime.of(2026, 9, 13, 8, 0, 0),
                    DateUtil.parse("2026-09-13 08:00:00"));
        }

        @Test
        @DisplayName("首尾空白会被裁掉")
        void trimsInput() {
            assertEquals(LocalDateTime.of(2026, 9, 13, 8, 0, 0),
                    DateUtil.parse("  2026-09-13 08:00:00  "));
        }

        @Test
        @DisplayName("null / 空串 / 纯空白一律返回 null（视为未传）")
        void blankReturnsNull() {
            assertNull(DateUtil.parse(null));
            assertNull(DateUtil.parse(""));
            assertNull(DateUtil.parse("   "));
        }

        @Test
        @DisplayName("ISO 格式 2026-09-13T08:00 视为非法，抛 400")
        void rejectsIsoFormat() {
            BusinessException e = assertThrows(BusinessException.class,
                    () -> DateUtil.parse("2026-09-13T08:00"));
            assertEquals(Result.CODE_BAD_REQUEST, e.getCode());
        }

        @Test
        @DisplayName("只写日期不带时间，严格模式视为非法")
        void rejectsDateOnly() {
            assertEquals(Result.CODE_BAD_REQUEST,
                    assertThrows(BusinessException.class,
                            () -> DateUtil.parse("2026-09-13")).getCode());
        }
    }

    @Nested
    @DisplayName("parseLenient · 宽松解析")
    class ParseLenient {

        @Test
        @DisplayName("带时间的按 yyyy-MM-dd HH:mm:ss 解析")
        void acceptsDateTime() {
            assertEquals(LocalDateTime.of(2026, 9, 13, 8, 0, 0),
                    DateUtil.parseLenient("2026-09-13 08:00:00"));
        }

        @Test
        @DisplayName("只写日期的补成当天零点")
        void dateOnlyBecomesStartOfDay() {
            LocalDateTime t = DateUtil.parseLenient("2026-09-13");
            assertEquals(LocalDate.of(2026, 9, 13), t.toLocalDate());
            assertEquals(0, t.getHour());
            assertEquals(0, t.getMinute());
            assertEquals(0, t.getSecond());
        }

        @Test
        @DisplayName("null / 空串返回 null")
        void blankReturnsNull() {
            assertNull(DateUtil.parseLenient(null));
            assertNull(DateUtil.parseLenient("  "));
        }

        @Test
        @DisplayName("两种格式都不匹配时抛 400")
        void rejectsGarbage() {
            assertEquals(Result.CODE_BAD_REQUEST,
                    assertThrows(BusinessException.class,
                            () -> DateUtil.parseLenient("13/09/2026")).getCode());
        }
    }

    @Nested
    @DisplayName("yearOf / hoursBetween / plusHours · 数值计算")
    class Arithmetic {

        @Test
        @DisplayName("yearOf 取年份，null 返回 0")
        void yearOf() {
            assertEquals(2026, DateUtil.yearOf(LocalDateTime.of(2026, 9, 13, 8, 0)));
            assertEquals(0, DateUtil.yearOf(null));
        }

        @Test
        @DisplayName("hoursBetween 同向为正、反向为负、含半小时精度")
        void hoursBetween() {
            LocalDateTime a = LocalDateTime.of(2026, 9, 13, 8, 0);
            assertEquals(2.0D, DateUtil.hoursBetween(a, a.plusHours(2)), 1e-9);
            assertEquals(-2.0D, DateUtil.hoursBetween(a.plusHours(2), a), 1e-9);
            assertEquals(0.5D, DateUtil.hoursBetween(a, a.plusMinutes(30)), 1e-9);
        }

        @Test
        @DisplayName("hoursBetween 任一入参为 null 时返回 0")
        void hoursBetweenNullSafe() {
            assertEquals(0D, DateUtil.hoursBetween(null, LocalDateTime.now()), 1e-9);
            assertEquals(0D, DateUtil.hoursBetween(LocalDateTime.now(), null), 1e-9);
        }

        @Test
        @DisplayName("plusHours 支持小数与负数，可跨天")
        void plusHours() {
            LocalDateTime base = LocalDateTime.of(2026, 9, 13, 0, 0);
            assertEquals(LocalDateTime.of(2026, 9, 13, 1, 30), DateUtil.plusHours(base, 1.5));
            assertEquals(LocalDateTime.of(2026, 9, 12, 22, 0), DateUtil.plusHours(base, -2));
        }

        @Test
        @DisplayName("plusHours 基准为 null 时返回 null")
        void plusHoursNullSafe() {
            assertNull(DateUtil.plusHours(null, 3));
        }
    }

    @Nested
    @DisplayName("Timestamp 互转与 now")
    class Conversion {

        @Test
        @DisplayName("LocalDateTime 与 Timestamp 可无损往返")
        void roundTrip() {
            LocalDateTime t = LocalDateTime.of(2026, 9, 13, 8, 30, 5);
            Timestamp ts = DateUtil.toTimestamp(t);
            assertEquals(t, DateUtil.toLocalDateTime(ts));
        }

        @Test
        @DisplayName("null 互转均返回 null")
        void conversionNullSafe() {
            assertNull(DateUtil.toTimestamp(null));
            assertNull(DateUtil.toLocalDateTime((Timestamp) null));
        }

        @Test
        @DisplayName("now 落在调用前后的时间区间内")
        void nowIsCurrent() {
            LocalDateTime before = LocalDateTime.now();
            LocalDateTime actual = DateUtil.now();
            LocalDateTime after = LocalDateTime.now();
            assertFalse(actual.isBefore(before), "now() 不应早于调用前时刻");
            assertFalse(actual.isAfter(after), "now() 不应晚于调用后时刻");
        }
    }

    @Test
    @DisplayName("工具类不可实例化：不存在公开构造，仅有的构造器为 private")
    void cannotBeInstantiated() {
        assertThrows(NoSuchMethodException.class,
                () -> DateUtil.class.getConstructor(), "不应存在公开的无参构造");
        Constructor<?>[] declared = DateUtil.class.getDeclaredConstructors();
        assertEquals(1, declared.length, "工具类应只有一个构造器");
        assertTrue(java.lang.reflect.Modifier.isPrivate(declared[0].getModifiers()),
                "该构造器必须是 private");
    }
}
