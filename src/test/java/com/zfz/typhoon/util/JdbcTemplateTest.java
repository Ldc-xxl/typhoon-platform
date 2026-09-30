package com.zfz.typhoon.util;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JdbcTemplate} 单元测试。
 *
 * <h3>为什么连真实数据库</h3>
 * <p>本类的职责是"把 JDBC 的样板代码收敛到一处"——参数绑定、资源释放、
 * 异常包装这三件事只有在真实驱动上跑一遍才有意义。用 mock 造一个
 * {@code PreparedStatement} 只能验证"我调用了 setString"，验证不了
 * "占位符个数与参数对得上"、"中文不会写坏"这类真问题。
 *
 * <h3>隔离手段</h3>
 * <p>用例自建一张**名字带随机后缀**的临时表（{@code zz_ut_scratch_<hex>}），
 * {@code @AfterAll} 删掉。带后缀是为了让并行跑的多个测试 JVM 不会互相踩同一张表。
 *
 * <p>连不上库时 {@code assumeTrue} 整体跳过。
 *
 * @author 追风者项目组
 */
@DisplayName("JdbcTemplate：轻量 JDBC 模板")
class JdbcTemplateTest {

    /** 每个 JVM 一张独占的临时表，避免并行测试互相干扰 */
    private static final String TABLE =
            "zz_ut_scratch_" + Long.toHexString(System.nanoTime());

    @BeforeAll
    static void createScratchTable() {
        Assumptions.assumeTrue(DBUtil.isAvailable(),
                "本机数据库不可用（" + DBUtil.getInitError() + "），跳过 JdbcTemplate 测试");
        JdbcTemplate.update("CREATE TABLE " + TABLE + " ("
                + " id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                + " name VARCHAR(64) DEFAULT NULL,"
                + " amount DECIMAL(12,2) DEFAULT NULL,"
                + " created_at DATETIME DEFAULT NULL"
                + ") DEFAULT CHARSET=utf8mb4");
    }

    @AfterAll
    static void dropScratchTable() {
        if (DBUtil.isAvailable()) {
            try {
                JdbcTemplate.update("DROP TABLE IF EXISTS " + TABLE);
            } catch (RuntimeException ignored) {
                // 清理失败不影响测试结论，交由人工排查
            }
        }
    }

    /** 清空临时表，保证用例之间互不影响 */
    private static void truncate() {
        JdbcTemplate.update("DELETE FROM " + TABLE);
    }

    /* ================================================================== */
    /*  查询                                                              */
    /* ================================================================== */

    @Nested
    @DisplayName("query：列表查询与行映射")
    class Query {

        @Test
        @DisplayName("无参数查询：返回映射后的列表")
        void queryWithoutParams() {
            List<Integer> list = JdbcTemplate.query("SELECT 1", new RowMapper<Integer>() {
                @Override
                public Integer map(ResultSet rs) throws SQLException {
                    return Integer.valueOf(rs.getInt(1));
                }
            });
            assertEquals(1, list.size());
            assertEquals(1, list.get(0).intValue());
        }

        @Test
        @DisplayName("带参数查询：String / Integer / Long / BigDecimal 都能正确绑定")
        void queryBindsCommonTypes() {
            String s = JdbcTemplate.queryString("SELECT ? AS v", "追风者");
            assertEquals("追风者", s, "中文参数应能原样往返（连接串已声明 utf8）");

            assertEquals(7, JdbcTemplate.queryInt("SELECT ? AS v", Integer.valueOf(7)).intValue());
            assertEquals(8L, JdbcTemplate.queryLong("SELECT ? AS v", Long.valueOf(8L)).longValue());

            List<BigDecimal> amounts = JdbcTemplate.query("SELECT ? AS v",
                    new RowMapper<BigDecimal>() {
                        @Override
                        public BigDecimal map(ResultSet rs) throws SQLException {
                            return rs.getBigDecimal(1);
                        }
                    }, new BigDecimal("1.25"));
            assertEquals(1, amounts.size());
            assertEquals(0, new BigDecimal("1.25").compareTo(amounts.get(0)));
        }

        @Test
        @DisplayName("mapper 返回 null 的行被跳过，结果列表不含 null 元素")
        void nullMappedRowsAreSkipped() {
            List<String> list = JdbcTemplate.query("SELECT 1 AS v UNION ALL SELECT 2",
                    new RowMapper<String>() {
                        @Override
                        public String map(ResultSet rs) throws SQLException {
                            return rs.getInt(1) == 2 ? "two" : null;
                        }
                    });
            assertEquals(1, list.size());
            assertEquals("two", list.get(0));
        }

        @Test
        @DisplayName("无结果时返回空列表而不是 null（调用方可直接 for 循环）")
        void emptyResultIsEmptyListNotNull() {
            List<String> list = JdbcTemplate.query(
                    "SELECT name FROM " + TABLE + " WHERE 1 = 0", rs -> rs.getString(1));
            assertNotNull(list);
            assertTrue(list.isEmpty());
        }

        @Test
        @DisplayName("SQL 写错时包装为 DataAccessException，并保留原始 SQL 便于排障")
        void badSqlIsWrapped() {
            DataAccessException e = assertThrows(DataAccessException.class,
                    () -> JdbcTemplate.query("SELECT nope FROM " + TABLE + "", rs -> rs.getString(1)));
            assertTrue(e.getMessage().contains("查询失败"), "实际：" + e.getMessage());
            assertEquals("SELECT nope FROM " + TABLE, e.getSql());
            assertNotNull(e.getCause(), "原始 SQLException 应作为 cause 保留");
        }
    }

    /* ================================================================== */
    /*  单行查询                                                          */
    /* ================================================================== */

    @Nested
    @DisplayName("queryOne：单行查询与 LIMIT 幂等")
    class QueryOne {

        @Test
        @DisplayName("有结果时返回第一条")
        void returnsFirstRow() {
            Integer v = JdbcTemplate.queryOne("SELECT 42 AS v",
                    rs -> Integer.valueOf(rs.getInt(1)));
            assertEquals(42, v.intValue());
        }

        @Test
        @DisplayName("无结果时返回 null")
        void returnsNullWhenEmpty() {
            assertNull(JdbcTemplate.queryOne("SELECT name FROM " + TABLE + " WHERE 1 = 0",
                    rs -> rs.getString(1)));
        }

        @Test
        @DisplayName("SQL 自带 LIMIT 1 时不再重复追加（否则 MySQL 直接报语法错误）")
        void doesNotDuplicateExistingLimit() {
            // 这是修复前真实踩过的坑：... ORDER BY x LIMIT 1 LIMIT 1 → MySQL 语法错误
            Integer v = JdbcTemplate.queryOne("SELECT 5 AS v LIMIT 1",
                    rs -> Integer.valueOf(rs.getInt(1)));
            assertEquals(5, v.intValue());

            Integer withSemicolon = JdbcTemplate.queryOne("SELECT 6 AS v LIMIT 1;",
                    rs -> Integer.valueOf(rs.getInt(1)));
            assertEquals(6, withSemicolon.intValue());

            Integer withOffsetForm = JdbcTemplate.queryOne("SELECT 7 AS v LIMIT 0, 1",
                    rs -> Integer.valueOf(rs.getInt(1)));
            assertEquals(7, withOffsetForm.intValue());

            Integer withPlaceholder = JdbcTemplate.queryOne("SELECT 8 AS v LIMIT ?",
                    rs -> Integer.valueOf(rs.getInt(1)), Integer.valueOf(1));
            assertEquals(8, withPlaceholder.intValue());
        }

        @Test
        @DisplayName("SQL 没有 LIMIT 时自动补一个")
        void appendsLimitWhenMissing() {
            JdbcTemplate.update("INSERT INTO " + TABLE + " (name) VALUES ('a'), ('b')");
            assertEquals(2, JdbcTemplate.queryInt("SELECT COUNT(*) FROM " + TABLE).intValue());

            String first = JdbcTemplate.queryOne("SELECT name FROM " + TABLE + " ORDER BY id",
                    rs -> rs.getString(1));
            assertEquals("a", first, "应只取到第一条");
        }

        @Test
        @DisplayName("SQL 为 null 或空白时进入加 LIMIT 的兜底分支，最终以数据访问异常收场")
        void nullOrBlankSqlFailsGracefully() {
            assertThrows(DataAccessException.class,
                    () -> JdbcTemplate.queryOne(null, rs -> rs.getString(1)));
            assertThrows(DataAccessException.class,
                    () -> JdbcTemplate.queryOne("   ", rs -> rs.getString(1)));
        }
    }

    /* ================================================================== */
    /*  标量查询                                                          */
    /* ================================================================== */

    @Nested
    @DisplayName("标量查询：queryInt / queryLong / queryDouble / queryString")
    class Scalars {

        @Test
        @DisplayName("queryInt 取出整数")
        void queryIntValue() {
            assertEquals(3, JdbcTemplate.queryInt("SELECT 3").intValue());
        }

        @Test
        @DisplayName("queryLong 取出长整数（避免 int 溢出）")
        void queryLongValue() {
            assertEquals(123456789012L, JdbcTemplate.queryLong("SELECT 123456789012").longValue());
        }

        @Test
        @DisplayName("queryDouble 取出浮点数")
        void queryDoubleValue() {
            assertEquals(1.5D, JdbcTemplate.queryDouble("SELECT 1.5").doubleValue(), 0.0001D);
        }

        @Test
        @DisplayName("queryString 取出字符串")
        void queryStringValue() {
            assertEquals("abc", JdbcTemplate.queryString("SELECT 'abc'"));
        }

        @Test
        @DisplayName("无结果时全部返回 null（而不是 0 或空串）")
        void scalarsAreNullWhenNoRow() {
            String empty = "SELECT 1 WHERE 1 = 0";
            assertNull(JdbcTemplate.queryInt(empty));
            assertNull(JdbcTemplate.queryLong(empty));
            assertNull(JdbcTemplate.queryDouble(empty));
            assertNull(JdbcTemplate.queryString(empty));
        }

        @Test
        @DisplayName("查询结果本身为 NULL 时也返回 null（wasNull 分支）")
        void scalarNullValueIsNull() {
            assertNull(JdbcTemplate.queryInt("SELECT NULL"));
            assertNull(JdbcTemplate.queryLong("SELECT NULL"));
            assertNull(JdbcTemplate.queryDouble("SELECT NULL"));
        }

        @Test
        @DisplayName("queryInt 对非整数值做截断（intValue 语义）")
        void queryIntTruncates() {
            assertEquals(3, JdbcTemplate.queryInt("SELECT 3.9").intValue());
        }

        @Test
        @DisplayName("标量查询 SQL 出错时同样包装成 DataAccessException")
        void scalarBadSqlIsWrapped() {
            assertThrows(DataAccessException.class, () -> JdbcTemplate.queryString("SELECT nope FROM " + TABLE));
            assertThrows(DataAccessException.class, () -> JdbcTemplate.queryInt("SELECT nope FROM " + TABLE));
        }
    }

    /* ================================================================== */
    /*  写入                                                              */
    /* ================================================================== */

    @Nested
    @DisplayName("写入：update / insert / batchUpdate")
    class Writes {

        @Test
        @DisplayName("update 返回受影响行数")
        void updateReturnsAffectedRows() {
            truncate();
            int inserted = JdbcTemplate.update("INSERT INTO " + TABLE
                    + " (name, amount, created_at) VALUES (?, ?, ?)",
                    "台风A", new BigDecimal("12.34"), LocalDateTime.of(2026, 9, 13, 8, 0, 0));
            assertEquals(1, inserted);
            assertEquals(1, JdbcTemplate.queryInt("SELECT COUNT(*) FROM " + TABLE).intValue());
        }

        @Test
        @DisplayName("update 无匹配行时返回 0")
        void updateAffectsNothingWhenNoMatch() {
            truncate();
            assertEquals(0, JdbcTemplate.update("UPDATE " + TABLE + " SET name = ? WHERE id = ?",
                    "x", Long.valueOf(999999L)));
        }

        @Test
        @DisplayName("insert 返回自增主键")
        void insertReturnsGeneratedKey() {
            truncate();
            Long id = JdbcTemplate.insert("INSERT INTO " + TABLE + " (name) VALUES (?)", "台风B");
            assertNotNull(id, "驱动应回填自增主键");
            assertTrue(id.longValue() > 0L);
        }

        @Test
        @DisplayName("batchUpdate 空集合或 null 时直接返回 0，不碰数据库")
        void batchUpdateShortCircuits() {
            assertEquals(0, JdbcTemplate.batchUpdate("INSERT INTO " + TABLE + " (name) VALUES (?)", null));
            assertEquals(0, JdbcTemplate.batchUpdate("INSERT INTO " + TABLE + " (name) VALUES (?)",
                    Collections.<Object[]>emptyList()));
        }

        @Test
        @DisplayName("batchUpdate 批量插入并返回执行行数之和")
        void batchUpdateInsertsAll() {
            truncate();
            List<Object[]> rows = new ArrayList<Object[]>();
            rows.add(new Object[]{"行一"});
            rows.add(new Object[]{"行二"});
            rows.add(new Object[]{"行三"});
            assertEquals(3, JdbcTemplate.batchUpdate("INSERT INTO " + TABLE + " (name) VALUES (?)", rows));
            assertEquals(3, JdbcTemplate.queryInt("SELECT COUNT(*) FROM " + TABLE).intValue());
        }

        @Test
        @DisplayName("batchUpdate 同一条 SQL 可用于不同参数的批量 UPDATE")
        void batchUpdateForUpdates() {
            truncate();
            JdbcTemplate.update("INSERT INTO " + TABLE + " (name) VALUES ('a'), ('b')");
            List<Object[]> rows = new ArrayList<Object[]>();
            rows.add(new Object[]{"A", "a"});
            rows.add(new Object[]{"B", "b"});
            assertEquals(2, JdbcTemplate.batchUpdate(
                    "UPDATE " + TABLE + " SET name = ? WHERE name = ?", rows));
        }

        @Test
        @DisplayName("写入 SQL 出错时分别以「更新失败 / 新增失败 / 批量更新失败」包装")
        void writeErrorsAreWrapped() {
            DataAccessException u = assertThrows(DataAccessException.class,
                    () -> JdbcTemplate.update("UPDATE no_such_table SET x = 1"));
            assertTrue(u.getMessage().startsWith("更新失败"), "实际：" + u.getMessage());

            DataAccessException i = assertThrows(DataAccessException.class,
                    () -> JdbcTemplate.insert("INSERT INTO no_such_table (x) VALUES (1)"));
            assertTrue(i.getMessage().startsWith("新增失败"), "实际：" + i.getMessage());

            DataAccessException b = assertThrows(DataAccessException.class,
                    () -> JdbcTemplate.batchUpdate("INSERT INTO no_such_table (x) VALUES (?)",
                            Collections.<Object[]>singletonList(new Object[]{"x"})));
            assertTrue(b.getMessage().startsWith("批量更新失败"), "实际：" + b.getMessage());
        }
    }

    /* ================================================================== */
    /*  参数绑定                                                          */
    /* ================================================================== */

    @Nested
    @DisplayName("参数绑定：各类型映射到正确的 JDBC setter")
    class ParameterBinding {

        @Test
        @DisplayName("null 参数绑定为 SQL NULL")
        void bindsNull() {
            assertNull(JdbcTemplate.queryString("SELECT ? AS v", new Object[]{null}));
        }

        @Test
        @DisplayName("Boolean 映射为 0 / 1（MySQL 无原生布尔）")
        void bindsBooleanAsTinyInt() {
            assertEquals(1, JdbcTemplate.queryInt("SELECT ? AS v", Boolean.TRUE).intValue());
            assertEquals(0, JdbcTemplate.queryInt("SELECT ? AS v", Boolean.FALSE).intValue());
        }

        @Test
        @DisplayName("Short / Byte 走 setInt 提升")
        void bindsShortAndByte() {
            assertEquals(5, JdbcTemplate.queryInt("SELECT ? AS v", Short.valueOf((short) 5)).intValue());
            assertEquals(6, JdbcTemplate.queryInt("SELECT ? AS v", Byte.valueOf((byte) 6)).intValue());
        }

        @Test
        @DisplayName("Float 走 setDouble 提升，精度以 double 为准")
        void bindsFloatAsDouble() {
            assertEquals(2.5D, JdbcTemplate.queryDouble("SELECT ? AS v", Float.valueOf(2.5F)).doubleValue(), 0.0001D);
        }

        @Test
        @DisplayName("LocalDateTime 映射为 Timestamp")
        void bindsLocalDateTime() {
            String v = JdbcTemplate.queryString("SELECT ? AS v", LocalDateTime.of(2026, 9, 13, 8, 30, 5));
            assertTrue(v.startsWith("2026-09-13 08:30:05"), "实际：" + v);
        }

        @Test
        @DisplayName("LocalDate 取当天 00:00:00 作为时间戳")
        void bindsLocalDate() {
            String v = JdbcTemplate.queryString("SELECT ? AS v", LocalDate.of(2026, 9, 13));
            assertTrue(v.startsWith("2026-09-13"), "实际：" + v);
        }

        @Test
        @DisplayName("java.util.Date 映射为 Timestamp")
        void bindsUtilDate() {
            String v = JdbcTemplate.queryString("SELECT ? AS v", new java.util.Date(0L));
            assertNotNull(v);
        }

        @Test
        @DisplayName("未特判的类型（如 LocalTime）回落到 setObject，仍能正常绑定")
        void bindsUnknownTypeViaSetObject() {
            String v = JdbcTemplate.queryString("SELECT ? AS v", LocalTime.of(9, 30, 15));
            assertNotNull(v, "兜底分支应能把参数交给驱动自行识别");
            assertTrue(v.contains("9:30") || v.contains("09:30"),
                    "实际：" + v + "（驱动可能按序列化字节处理，只要求能绑定）");
        }

        @Test
        @DisplayName("参数个数与占位符不匹配时以 DataAccessException 收场")
        void mismatchedParamCountFails() {
            assertThrows(DataAccessException.class,
                    () -> JdbcTemplate.queryString("SELECT ? AS a, ? AS b", "只有一个"));
        }

        @Test
        @DisplayName("参数数组整体为 null 时视为无参数（bind 直接返回）")
        void nullParamsArrayIsTreatedAsNoParams() {
            Object[] none = null;
            assertEquals("ok", JdbcTemplate.queryString("SELECT 'ok' AS v", none));
            assertEquals("ok", JdbcTemplate.queryString("SELECT 'ok' AS v", new Object[0]));
        }
    }

    @Test
    @DisplayName("工具类不可实例化：不存在公开构造，仅有的构造器为 private")
    void utilityClassCannotBeInstantiated() {
        assertThrows(NoSuchMethodException.class,
                () -> JdbcTemplate.class.getConstructor(), "不应存在公开的无参构造");
        java.lang.reflect.Constructor<?>[] declared = JdbcTemplate.class.getDeclaredConstructors();
        assertEquals(1, declared.length, "工具类应只有一个构造器");
        assertTrue(java.lang.reflect.Modifier.isPrivate(declared[0].getModifiers()),
                "该构造器必须是 private");
    }

}
