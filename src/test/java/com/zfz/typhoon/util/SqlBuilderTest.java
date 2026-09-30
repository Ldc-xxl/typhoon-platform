package com.zfz.typhoon.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SqlBuilder} 单元测试。
 *
 * <p>这个类承担"多条件组合检索"与"SQL 注入防线"两个职责，故断言落在两处：
 * <ol>
 *   <li><b>SQL 文本逐字比对</b>——条件拼接顺序、{@code WHERE 1 = 1} 的补位时机、
 *       LIMIT/OFFSET 的追加位置，全部写成完整期望串，不写 contains 式的松断言；</li>
 *   <li><b>参数数组</b>——凡是来自用户的值必须出现在参数数组里，
 *       且 SQL 文本中只能见到 {@code ?}。这是"值不进 SQL"的直接证据。</li>
 * </ol>
 */
@DisplayName("SqlBuilder · 动态 SQL 构造器")
class SqlBuilderTest {

    @Nested
    @DisplayName("基础结构")
    class Structure {

        @Test
        @DisplayName("只有 SELECT 与 FROM 时不产生 WHERE")
        void selectFromOnly() {
            SqlBuilder.Built built = SqlBuilder.select("t.*").from("typhoons t").build();
            assertEquals("SELECT t.* FROM typhoons t", built.getSql());
            assertEquals(0, built.getParams().length);
        }

        @Test
        @DisplayName("首个条件会自动补上 WHERE 1 = 1")
        void firstConditionInsertsWhereClause() {
            SqlBuilder.Built built = SqlBuilder.select("t.*").from("typhoons t")
                    .eq("t.status", "active").build();
            assertEquals("SELECT t.* FROM typhoons t WHERE 1 = 1 AND t.status = ?", built.getSql());
            assertArrayEquals(new Object[]{"active"}, built.getParams());
        }

        @Test
        @DisplayName("显式 where() 之后的条件不再重复补 WHERE")
        void explicitWhereSuppressesAutoWhere() {
            SqlBuilder.Built built = SqlBuilder.select("t.*").from("typhoons t")
                    .where("t.basin_id = b.id").eq("t.status", "active").build();
            assertEquals("SELECT t.* FROM typhoons t WHERE t.basin_id = b.id"
                    + " AND t.status = ?", built.getSql());
        }

        @Test
        @DisplayName("of(head) 若 head 已含 WHERE，后续条件直接接 AND")
        void ofWithWhereInHead() {
            SqlBuilder.Built built = SqlBuilder.of("UPDATE users SET last_login = ? WHERE id = ?")
                    .eq("t.enabled", 1).build();
            assertEquals("UPDATE users SET last_login = ? WHERE id = ? AND t.enabled = ?",
                    built.getSql());
        }

        @Test
        @DisplayName("of(head) 若 head 不含 WHERE，首个条件补 WHERE 1 = 1")
        void ofWithoutWhereInHead() {
            SqlBuilder.Built built = SqlBuilder.of("DELETE FROM typhoons")
                    .eq("t.typhoon_id", 13L).build();
            assertEquals("DELETE FROM typhoons WHERE 1 = 1 AND t.typhoon_id = ?", built.getSql());
            assertArrayEquals(new Object[]{13L}, built.getParams());
        }

        @Test
        @DisplayName("paramCount 与 build().getParams() 长度始终一致")
        void paramCountMatchesBuiltParams() {
            SqlBuilder sb = SqlBuilder.select("t.*").from("typhoons t")
                    .eq("t.status", "active").eq("t.basin", "西北太平洋").limit(10, 0);
            // 两个条件各 1 个 + LIMIT 的 limit/offset 共 2 个 = 4
            assertEquals(4, sb.paramCount());
            assertEquals(sb.paramCount(), sb.build().getParams().length);
        }

        @Test
        @DisplayName("Built.toString() 即 SQL 文本")
        void builtToStringIsSql() {
            SqlBuilder.Built built = SqlBuilder.select("1").build();
            assertEquals(built.getSql(), built.toString());
        }
    }

    @Nested
    @DisplayName("比较条件 · 空值自动跳过")
    class Comparisons {

        @Test
        @DisplayName("eq / ne / gt / ge / lt / le 各生成对应运算符")
        void comparisonOperators() {
            SqlBuilder.Built built = SqlBuilder.select("t.*").from("t")
                    .eq("t.a", 1).ne("t.b", 2).gt("t.c", 3).ge("t.d", 4).lt("t.e", 5).le("t.f", 6)
                    .build();
            assertEquals("SELECT t.* FROM t WHERE 1 = 1"
                    + " AND t.a = ?"
                    + " AND t.b <> ?"
                    + " AND t.c > ?"
                    + " AND t.d >= ?"
                    + " AND t.e < ?"
                    + " AND t.f <= ?", built.getSql());
            assertArrayEquals(new Object[]{1, 2, 3, 4, 5, 6}, built.getParams());
        }

        @Test
        @DisplayName("值为 null 或在空白串时全部跳过，且不会因跳过而多出 WHERE")
        void skipsBlankValues() {
            SqlBuilder.Built built = SqlBuilder.select("t.*").from("t")
                    .eq("t.a", null)
                    .ne("t.b", null)
                    .gt("t.c", null)
                    .ge("t.d", null)
                    .lt("t.e", null)
                    .le("t.f", null)
                    .eq("t.g", "   ")
                    .build();
            assertEquals("SELECT t.* FROM t", built.getSql());
            assertEquals(0, built.getParams().length);
        }

        @Test
        @DisplayName("数字 0 与布尔 false 不算空值，必须保留条件")
        void zeroIsNotBlank() {
            SqlBuilder.Built built = SqlBuilder.select("t.*").from("t")
                    .eq("t.seq", 0).eq("t.flag", Boolean.FALSE).build();
            assertEquals("SELECT t.* FROM t WHERE 1 = 1 AND t.seq = ? AND t.flag = ?", built.getSql());
            assertArrayEquals(new Object[]{0, Boolean.FALSE}, built.getParams());
        }

        @Test
        @DisplayName("LocalDateTime 值原样进参数数组，SQL 中只有 ?")
        void dateTimeGoesToParams() {
            LocalDateTime from = LocalDateTime.of(2026, 9, 1, 0, 0);
            SqlBuilder.Built built = SqlBuilder.select("t.*").from("t")
                    .ge("t.generate_time", from).build();
            assertEquals("SELECT t.* FROM t WHERE 1 = 1 AND t.generate_time >= ?", built.getSql());
            assertArrayEquals(new Object[]{from}, built.getParams());
        }
    }

    @Nested
    @DisplayName("like / likePrefix / in / eqBool / raw")
    class SpecialConditions {

        @Test
        @DisplayName("like 自动包裹百分号，并裁剪关键词首尾空白")
        void likeWrapsKeyword() {
            SqlBuilder.Built built = SqlBuilder.select("t.*").from("t")
                    .like("t.chinese_name", "  海葵  ").build();
            assertEquals("SELECT t.* FROM t WHERE 1 = 1 AND t.chinese_name LIKE ?", built.getSql());
            assertArrayEquals(new Object[]{"%海葵%"}, built.getParams());
        }

        @Test
        @DisplayName("likePrefix 只在尾部加百分号（为的是能用上索引）")
        void likePrefixAppendsSuffixOnly() {
            SqlBuilder.Built built = SqlBuilder.select("t.*").from("t")
                    .likePrefix("t.international_id", "2615").build();
            assertArrayEquals(new Object[]{"2615%"}, built.getParams());
        }

        @Test
        @DisplayName("like / likePrefix 关键词为空时跳过")
        void likeBlankSkipped() {
            assertEquals("SELECT t.* FROM t", SqlBuilder.select("t.*").from("t")
                    .like("t.a", null).like("t.b", "  ").likePrefix("t.c", null).build().getSql());
        }

        @Test
        @DisplayName("in 展开为对应个数的占位符，顺序与入参一致")
        void inExpandsPlaceholders() {
            SqlBuilder.Built built = SqlBuilder.select("t.*").from("t")
                    .in("t.typhoon_id", Arrays.asList(1L, 2L, 3L)).build();
            assertEquals("SELECT t.* FROM t WHERE 1 = 1 AND t.typhoon_id IN (?, ?, ?)", built.getSql());
            assertArrayEquals(new Object[]{1L, 2L, 3L}, built.getParams());
        }

        @Test
        @DisplayName("in 集合为 null 或空时跳过")
        void inBlankSkipped() {
            assertEquals("SELECT t.* FROM t", SqlBuilder.select("t.*").from("t")
                    .in("t.a", null).in("t.b", Collections.emptyList()).build().getSql());
        }

        @Test
        @DisplayName("eqBool 把布尔转成 tinyint 语义的 1 / 0")
        void eqBoolConvertsToTinyint() {
            SqlBuilder.Built built = SqlBuilder.select("t.*").from("t")
                    .eqBool("t.is_active", Boolean.TRUE)
                    .eqBool("t.is_deleted", Boolean.FALSE).build();
            assertEquals("SELECT t.* FROM t WHERE 1 = 1 AND t.is_active = ? AND t.is_deleted = ?",
                    built.getSql());
            assertArrayEquals(new Object[]{1, 0}, built.getParams());
        }

        @Test
        @DisplayName("eqBool 传 null 时跳过")
        void eqBoolNullSkipped() {
            assertEquals("SELECT t.* FROM t", SqlBuilder.select("t.*").from("t")
                    .eqBool("t.a", null).build().getSql());
        }

        @Test
        @DisplayName("raw 追加自定义条件并绑定可变参数；不传参数则不绑定")
        void rawAppendsConditionAndValues() {
            SqlBuilder.Built built = SqlBuilder.select("t.*").from("t")
                    .raw("DATE(t.obs_time) = ?", "2026-09-13")
                    .raw("(t.a = 1 OR t.b = 2)").build();
            assertEquals("SELECT t.* FROM t WHERE 1 = 1"
                    + " AND DATE(t.obs_time) = ?"
                    + " AND (t.a = 1 OR t.b = 2)", built.getSql());
            assertArrayEquals(new Object[]{"2026-09-13"}, built.getParams());
        }
    }

    @Nested
    @DisplayName("排序 / 分组 / 分页")
    class OrderAndPaging {

        @Test
        @DisplayName("orderBy / groupBy / having 依次追加，顺序即调用顺序")
        void appendsClausesInCallOrder() {
            SqlBuilder.Built built = SqlBuilder.select("t.basin, COUNT(*) AS c").from("t")
                    .eq("t.status", "active")
                    .groupBy("t.basin")
                    .having("COUNT(*) > ?")
                    .orderBy("t.basin DESC").build();
            assertEquals("SELECT t.basin, COUNT(*) AS c FROM t WHERE 1 = 1 AND t.status = ?"
                    + " GROUP BY t.basin"
                    + " HAVING COUNT(*) > ?"
                    + " ORDER BY t.basin DESC", built.getSql());
            assertArrayEquals(new Object[]{"active"}, built.getParams());
        }

        @Test
        @DisplayName("limit 生成 LIMIT ? OFFSET ?，两个值进参数数组")
        void limitAddsPagingParams() {
            SqlBuilder.Built built = SqlBuilder.select("t.*").from("t")
                    .orderBy("t.obs_time DESC").limit(20, 40).build();
            assertEquals("SELECT t.* FROM t ORDER BY t.obs_time DESC LIMIT ? OFFSET ?", built.getSql());
            assertArrayEquals(new Object[]{20, 40}, built.getParams());
        }

        @Test
        @DisplayName("limit <= 0 表示不分页，不追加任何片段")
        void nonPositiveLimitIsIgnored() {
            SqlBuilder.Built built = SqlBuilder.select("t.*").from("t").limit(0, 10).build();
            assertEquals("SELECT t.* FROM t", built.getSql());
            assertEquals(0, built.getParams().length);
        }

        @Test
        @DisplayName("offset 为负数时按 0 处理，避免 MySQL 语法错误")
        void negativeOffsetBecomesZero() {
            SqlBuilder.Built built = SqlBuilder.select("t.*").from("t").limit(10, -5).build();
            assertArrayEquals(new Object[]{10, 0}, built.getParams());
        }
    }

    @Nested
    @DisplayName("SQL 注入防线")
    class InjectionSafety {

        @Test
        @DisplayName("恶意输入只出现在参数数组，绝不进入 SQL 文本")
        void maliciousValueNeverEntersSql() {
            String evil = "' OR 1 = 1 -- ";
            SqlBuilder.Built built = SqlBuilder.select("t.*").from("typhoons t")
                    .eq("t.chinese_name", evil).build();
            assertEquals("SELECT t.* FROM typhoons t WHERE 1 = 1 AND t.chinese_name = ?", built.getSql());
            assertArrayEquals(new Object[]{evil}, built.getParams());
            assertTrue(built.getSql().indexOf("OR 1 = 1") < 0, "SQL 文本不应包含用户输入内容");
        }

        @Test
        @DisplayName("like 的恶意输入同样只进参数数组")
        void maliciousKeywordNeverEntersSql() {
            SqlBuilder.Built built = SqlBuilder.select("t.*").from("t")
                    .like("t.chinese_name", "'; DROP TABLE users; --").build();
            assertTrue(built.getSql().endsWith("LIKE ?"));
            assertArrayEquals(new Object[]{"%'; DROP TABLE users; --%"}, built.getParams());
        }

        @Test
        @DisplayName("in 的恶意元素同样只进参数数组")
        void maliciousInValuesNeverEnterSql() {
            SqlBuilder.Built built = SqlBuilder.select("t.*").from("t")
                    .in("t.city_name", Arrays.asList("北京", "') OR ('1'='1")).build();
            assertEquals("SELECT t.* FROM t WHERE 1 = 1 AND t.city_name IN (?, ?)", built.getSql());
        }
    }
}
