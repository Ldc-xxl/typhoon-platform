package com.zfz.typhoon.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link RsUtil} 单元测试。
 *
 * <p>本类存在的唯一理由是把 JDBC 的"NULL 悄悄变成 0"改成"如实返回 null"。
 * 这个转换做错的后果很隐蔽：某条记录没测到气压，页面上会显示 {@code 0 hPa}
 * 而不是 {@code --}，风圈半径会被算成 0，整张风险图跟着失真。
 * 因此每个 getter 都配一组<b>有值 / NULL 对照</b>断言。
 *
 * <p>结果集用 {@link FakeResultSet}（动态代理替身）伪造，而不是 Mockito ——
 * 原因见该类javadoc：{@code java.sql.ResultSet} 位于 JDK 模块内，
 * inline mock maker 改不动它。这个替身会如实模拟驱动的 {@code wasNull()} 契约，
 * 因此这几条断言真正验证的是"RsUtil 与 JDBC 驱动约定是否对齐"，
 * 而不只是"某个桩有没有被调到"。
 */
@DisplayName("RsUtil · 结果集字段读取")
class RsUtilTest {

    @Nested
    @DisplayName("数值列")
    class Numbers {

        @Test
        @DisplayName("getLong 有值时返回包装类型")
        void longWithValue() throws SQLException {
            ResultSet rs = FakeResultSet.create().with("typhoon_id", 13L).rs();
            assertEquals(Long.valueOf(13L), RsUtil.getLong(rs, "typhoon_id"));
        }

        @Test
        @DisplayName("getLong 遇 NULL 返回 null 而不是 0")
        void longWithNull() throws SQLException {
            ResultSet rs = FakeResultSet.create().rs();
            assertNull(RsUtil.getLong(rs, "typhoon_id"));
        }

        @Test
        @DisplayName("getInt 有值时返回包装类型")
        void intWithValue() throws SQLException {
            ResultSet rs = FakeResultSet.create().with("level", 14).rs();
            assertEquals(Integer.valueOf(14), RsUtil.getInt(rs, "level"));
        }

        @Test
        @DisplayName("getInt 遇 NULL 返回 null")
        void intWithNull() throws SQLException {
            ResultSet rs = FakeResultSet.create().rs();
            assertNull(RsUtil.getInt(rs, "level"));
        }

        @Test
        @DisplayName("getInt 取到 0 时返回 0 而非 null（0 是合法业务值）")
        void intZeroIsNotTreatedAsNull() throws SQLException {
            ResultSet rs = FakeResultSet.create().with("level", 0).rs();
            assertEquals(Integer.valueOf(0), RsUtil.getInt(rs, "level"));
        }

        @Test
        @DisplayName("getDouble 有值时返回包装类型")
        void doubleWithValue() throws SQLException {
            ResultSet rs = FakeResultSet.create().with("wind_speed", 45.5D).rs();
            assertEquals(Double.valueOf(45.5D), RsUtil.getDouble(rs, "wind_speed"));
        }

        @Test
        @DisplayName("getDouble 遇 NULL 返回 null")
        void doubleWithNull() throws SQLException {
            ResultSet rs = FakeResultSet.create().rs();
            assertNull(RsUtil.getDouble(rs, "wind_speed"));
        }

        @Test
        @DisplayName("getDecimal 保留精度（getBigDecimal 本身就是 null 安全的）")
        void decimalKeepsPrecision() throws SQLException {
            BigDecimal expected = new BigDecimal("1234.56");
            ResultSet rs = FakeResultSet.create().with("amount", expected).rs();
            assertEquals(expected, RsUtil.getDecimal(rs, "amount"));
        }

        @Test
        @DisplayName("getDecimal 遇 NULL 返回 null")
        void decimalWithNull() throws SQLException {
            ResultSet rs = FakeResultSet.create().rs();
            assertNull(RsUtil.getDecimal(rs, "amount"));
        }
    }

    @Nested
    @DisplayName("字符串与布尔列")
    class TextAndBoolean {

        @Test
        @DisplayName("getString 原样透传")
        void stringPassthrough() throws SQLException {
            ResultSet rs = FakeResultSet.create().with("chinese_name", "海葵").rs();
            assertEquals("海葵", RsUtil.getString(rs, "chinese_name"));
        }

        @Test
        @DisplayName("getString 遇 NULL 返回 null")
        void stringWithNull() throws SQLException {
            ResultSet rs = FakeResultSet.create().rs();
            assertNull(RsUtil.getString(rs, "chinese_name"));
        }

        @Test
        @DisplayName("getBoolean 把非零 tinyint 判为 true")
        void booleanFromNonZero() throws SQLException {
            ResultSet rs = FakeResultSet.create().with("is_active", 1).rs();
            assertEquals(Boolean.TRUE, RsUtil.getBoolean(rs, "is_active"));
        }

        @Test
        @DisplayName("getBoolean 把 0 判为 false")
        void booleanFromZero() throws SQLException {
            ResultSet rs = FakeResultSet.create().with("is_active", 0).rs();
            assertEquals(Boolean.FALSE, RsUtil.getBoolean(rs, "is_active"));
        }

        @Test
        @DisplayName("getBoolean 遇 NULL 返回 null（三态，不塌缩成 false）")
        void booleanWithNull() throws SQLException {
            ResultSet rs = FakeResultSet.create().rs();
            assertNull(RsUtil.getBoolean(rs, "is_active"));
        }
    }

    @Nested
    @DisplayName("时间列")
    class DateTime {

        @Test
        @DisplayName("getDateTime 把 Timestamp 转成 LocalDateTime")
        void convertsTimestamp() throws SQLException {
            LocalDateTime expected = LocalDateTime.of(2026, 9, 13, 8, 0, 0);
            ResultSet rs = FakeResultSet.create()
                    .with("obs_time", Timestamp.valueOf(expected)).rs();
            assertEquals(expected, RsUtil.getDateTime(rs, "obs_time"));
        }

        @Test
        @DisplayName("getDateTime 遇 NULL 返回 null")
        void nullTimestamp() throws SQLException {
            ResultSet rs = FakeResultSet.create().rs();
            assertNull(RsUtil.getDateTime(rs, "obs_time"));
        }
    }

    @Nested
    @DisplayName("异常传播")
    class Errors {

        @Test
        @DisplayName("SQLException 原样抛出，交给 DAO 层包装成 DataAccessException")
        void propagatesSqlException() {
            ResultSet rs = FakeResultSet.create()
                    .failing("missing_col", new SQLException("no such column")).rs();
            assertThrows(SQLException.class, () -> RsUtil.getString(rs, "missing_col"));
        }

        @Test
        @DisplayName("引用类型与数值类型的读取失败都向上抛，不被吞掉")
        void propagatesForNumericGettersToo() {
            ResultSet intRs = FakeResultSet.create()
                    .failing("missing_col", new SQLException("no such column")).rs();
            assertThrows(SQLException.class, () -> RsUtil.getInt(intRs, "missing_col"));

            ResultSet tsRs = FakeResultSet.create()
                    .failing("missing_col", new SQLException("no such column")).rs();
            assertThrows(SQLException.class, () -> RsUtil.getDateTime(tsRs, "missing_col"));
        }
    }

    @Nested
    @DisplayName("替身自身的契约")
    class DoubleItself {

        @Test
        @DisplayName("未设定的列一律读作 NULL，与真实驱动的空结果集行为一致")
        void unsetColumnsReadAsNull() throws SQLException {
            ResultSet rs = FakeResultSet.create().with("known", "x").rs();
            assertEquals("x", RsUtil.getString(rs, "known"));
            assertNull(RsUtil.getString(rs, "unknown"));
            assertNull(RsUtil.getInt(rs, "unknown"));
            assertNull(RsUtil.getLong(rs, "unknown"));
            assertNull(RsUtil.getDouble(rs, "unknown"));
            assertNull(RsUtil.getBoolean(rs, "unknown"));
            assertNull(RsUtil.getDecimal(rs, "unknown"));
            assertNull(RsUtil.getDateTime(rs, "unknown"));
        }
    }
}
