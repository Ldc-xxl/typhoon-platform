package com.zfz.typhoon.dao.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TyphoonQuery} 单元测试。
 *
 * <p>查询条件对象是"前端筛选表单"与"DAO 动态 SQL"之间唯一的契约层：
 * 前端传什么 → 这里收什么 → {@code SqlBuilder} 拼什么。
 * 它的<b>默认值</b>尤其关键 —— 分页上限默认是 20，如果哪天被改成 0，
 * 列表页会一次性把整表拉出来，数据量大时直接把应用拖垮。
 *
 * @author 追风者项目组
 */
@DisplayName("TyphoonQuery：台风查询条件")
class TyphoonQueryTest {

    @Nested
    @DisplayName("默认值")
    class Defaults {

        @Test
        @DisplayName("新建对象的所有筛选条件都是「不筛选」")
        void noFilterByDefault() {
            TyphoonQuery q = new TyphoonQuery();
            assertNull(q.getKeyword());
            assertNull(q.getYear());
            assertNull(q.getBasin());
            assertNull(q.getStatus());
            assertNull(q.getMinWindForce());
            assertNull(q.getArchived());
            assertNull(q.getGenerateFrom());
            assertNull(q.getGenerateTo());
        }

        @Test
        @DisplayName("分页默认值：limit=0 表示「不分页」，由 DAO 决定是否兜底")
        void pagingDefaults() {
            TyphoonQuery q = new TyphoonQuery();
            assertEquals(0, q.getLimit(),
                    "按类注释，limit<=0 表示不分页；调用方若要分页必须显式 setLimit");
            assertEquals(0, q.getOffset(), "默认应从第一条开始");
        }

        @Test
        @DisplayName("排序默认为空 —— 这是一处需要留意的约定")
        void sortDefaultsToNull() {
            TyphoonQuery q = new TyphoonQuery();
            assertNull(q.getSort(),
                    "sort 默认 null，由 DAO 内部的白名单兜底成默认排序；"
                            + "若哪天 DAO 不再兜底，没有 ORDER BY 会让分页出现重复项");
        }

    }

    @Nested
    @DisplayName("便捷构造")
    class Factories {

        @Test
        @DisplayName("byStatus：只按状态过滤")
        void byStatus() {
            TyphoonQuery q = TyphoonQuery.byStatus("ACTIVE");
            assertEquals("ACTIVE", q.getStatus());
            assertNull(q.getKeyword());
        }

        @Test
        @DisplayName("of：关键词 + 状态")
        void of() {
            TyphoonQuery q = TyphoonQuery.of("山竹", "FINISHED");
            assertEquals("山竹", q.getKeyword());
            assertEquals("FINISHED", q.getStatus());
        }
    }

    @Nested
    @DisplayName("赋值与读取")
    class Accessors {

        @Test
        @DisplayName("所有条件都能设进去再原样读出来")
        void roundTrip() {
            TyphoonQuery q = new TyphoonQuery();
            LocalDateTime from = LocalDateTime.of(2024, 1, 1, 0, 0);
            LocalDateTime to = LocalDateTime.of(2025, 12, 31, 23, 59);

            q.setKeyword("山竹");
            q.setYear(Integer.valueOf(2024));
            q.setBasin("西北太平洋");
            q.setStatus("ACTIVE");
            q.setMinWindForce(Integer.valueOf(12));
            q.setArchived(Boolean.FALSE);
            q.setGenerateFrom(from);
            q.setGenerateTo(to);
            q.setSort("wind_desc");
            q.setLimit(50);
            q.setOffset(20);

            assertEquals("山竹", q.getKeyword());
            assertEquals(Integer.valueOf(2024), q.getYear());
            assertEquals("西北太平洋", q.getBasin());
            assertEquals("ACTIVE", q.getStatus());
            assertEquals(Integer.valueOf(12), q.getMinWindForce());
            assertEquals(Boolean.FALSE, q.getArchived());
            assertEquals(from, q.getGenerateFrom());
            assertEquals(to, q.getGenerateTo());
            assertEquals("wind_desc", q.getSort());
            assertEquals(50, q.getLimit());
            assertEquals(20, q.getOffset());
        }

        @Test
        @DisplayName("允许把条件改回「不筛选」（传 null）")
        void nullableReset() {
            TyphoonQuery q = new TyphoonQuery();
            q.setKeyword("山竹");
            q.setKeyword(null);
            assertNull(q.getKeyword(), "置回 null 才能让 SqlBuilder 跳过该条件");
        }

        @Test
        @DisplayName("空字符串关键字应被视为「未填」——由调用方判断，这里如实保留")
        void blankKeywordIsPreserved() {
            TyphoonQuery q = new TyphoonQuery();
            q.setKeyword("   ");
            assertEquals("   ", q.getKeyword());
            assertTrue(q.getKeyword().trim().isEmpty(), "空白串如实保留，由调用方决定是否视为未填");
        }
    }
}
