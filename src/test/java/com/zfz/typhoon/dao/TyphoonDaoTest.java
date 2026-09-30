package com.zfz.typhoon.dao;

import com.zfz.typhoon.dao.impl.TyphoonDaoImpl;
import com.zfz.typhoon.dao.query.TyphoonQuery;
import com.zfz.typhoon.model.Typhoon;
import com.zfz.typhoon.util.DBUtil;
import com.zfz.typhoon.util.JdbcTemplate;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TyphoonDaoImpl} 集成测试：连本机 3306 的真实库跑真实 SQL。
 *
 * <h3>为什么 DAO 用集成测试而不是 mock</h3>
 * <p>DAO 的全部价值就是"这句 SQL 能跑通、字段映射没写错"。用 mock 造一个
 * {@code ResultSet} 的话，测试只能证明"我调了 getString("name")"，
 * 证明不了"表里确实有 name 这列"、"LEFT JOIN 没把行数放大"。
 * 真正的字段名/类型错误只有在真实库上才会暴露 —— 这也是本项目此前
 * 反复出现"列名写错直到联调才被发现"的根因。
 *
 * <h3>数据安全</h3>
 * <p>只读用例直接跑；需要写入的用例（insert / update / delete）全部用
 * <b>自建自删</b>的数据，并在 {@code finally} 里回滚删除，
 * 不污染演示数据。
 *
 * @author 追风者项目组
 */
@DisplayName("TyphoonDaoImpl：台风档案数据访问")
class TyphoonDaoTest {

    private static TyphoonDao dao;
    private static long sampleId;

    @BeforeAll
    static void setUp() {
        Assumptions.assumeTrue(DBUtil.isAvailable(),
                "本机数据库不可用（" + DBUtil.getInitError() + "），跳过 DAO 集成测试");
        dao = new TyphoonDaoImpl();
        Long id = JdbcTemplate.queryLong("SELECT MIN(typhoon_id) FROM typhoons");
        sampleId = id == null ? 0L : id.longValue();
    }

    private static TyphoonQuery query() {
        TyphoonQuery q = new TyphoonQuery();
        q.setLimit(10);
        q.setOffset(0);
        return q;
    }

    /* ================================================================== */
    /*  读                                                                 */
    /* ================================================================== */

    @Test
    @DisplayName("findById：存在的 ID 返回完整档案，不存在的返回 null")
    void findById() {
        Typhoon t = dao.findById(sampleId);
        assertNotNull(t, "库里应存在 typhoon_id=" + sampleId);
        assertEquals(Long.valueOf(sampleId), t.getTyphoonId());
        assertNotNull(t.getChineseName(), "台风中文名是前端列表的必显示字段");
        assertNull(dao.findById(-999L), "不存在的 ID 应返回 null 而不是空对象");
    }

    @Test
    @DisplayName("findByInternationalId：按国际编号反查")
    void findByInternationalId() {
        String code = JdbcTemplate.queryString(
                "SELECT international_id FROM typhoons WHERE typhoon_id = ?", Long.valueOf(sampleId));
        Assumptions.assumeTrue(code != null, "样例台风没有国际编号，跳过");
        Typhoon t = dao.findByInternationalId(code);
        assertNotNull(t);
        assertEquals(code, t.getInternationalId());
        assertNull(dao.findByInternationalId("NO-SUCH-CODE-9999"));
    }

    @Test
    @DisplayName("listAll：返回非空列表，且每条都有主键")
    void listAll() {
        List<Typhoon> rows = dao.listAll();
        assertNotNull(rows);
        assertFalse(rows.isEmpty(), "演示库里应有台风数据");
        for (Typhoon t : rows) {
            assertNotNull(t.getTyphoonId(), "映射漏了主键会导致后续所有链路取不到 ID");
        }
    }

    @Test
    @DisplayName("listByStatus：按状态过滤，结果状态一致")
    void listByStatus() {
        // 库里状态是大写的 ACTIVE / FINISHED；MySQL 默认排序规则不区分大小写，
        // 所以按 "active" 也能查到，断言时统一转大写
        List<Typhoon> rows = dao.listByStatus("active");
        assertNotNull(rows);
        for (Typhoon t : rows) {
            assertEquals("ACTIVE", t.getStatus().toUpperCase());
        }
    }

    @Test
    @DisplayName("search：关键字 / 年份 / 洋盆 / 强度下限 各条件都能下推到 SQL")
    void searchByConditions() {
        TyphoonQuery byKeyword = query();
        byKeyword.setKeyword("台风");
        List<Typhoon> kw = dao.search(byKeyword);
        assertNotNull(kw);

        TyphoonQuery byYear = query();
        byYear.setYear(Integer.valueOf(2024));
        assertNotNull(dao.search(byYear));

        TyphoonQuery byBasin = query();
        byBasin.setBasin("西北太平洋");
        assertNotNull(dao.search(byBasin));

        TyphoonQuery byForce = query();
        byForce.setMinWindForce(Integer.valueOf(12));
        assertNotNull(dao.search(byForce));

        TyphoonQuery byRange = query();
        byRange.setGenerateFrom(LocalDateTime.of(2000, 1, 1, 0, 0));
        byRange.setGenerateTo(LocalDateTime.of(2030, 12, 31, 23, 59));
        assertNotNull(dao.search(byRange));

        TyphoonQuery sorted = query();
        sorted.setSort("wind_desc");
        assertNotNull(dao.search(sorted));

        TyphoonQuery archived = query();
        archived.setArchived(Boolean.FALSE);
        assertNotNull(dao.search(archived));

        TyphoonQuery paged = query();
        paged.setLimit(3);
        paged.setOffset(0);
        assertTrue(dao.search(paged).size() <= 3, "limit 必须生效，否则大数据量下会拖垮页面");
    }

    @Test
    @DisplayName("countByCondition 与 search 的行数口径一致")
    void countMatchesSearch() {
        TyphoonQuery q = query();
        q.setLimit(1000);
        int count = dao.countByCondition(q);
        int size = dao.search(q).size();
        assertEquals(size, count, "分页用的总数必须与实际返回行数同口径，否则前端会显示错误的页码");
    }

    @Test
    @DisplayName("listYears / listBasins：筛选项下拉的数据来源")
    void listYearsAndBasins() {
        List<Integer> years = dao.listYears();
        assertNotNull(years);
        for (Integer y : years) {
            assertNotNull(y, "年份列表里不能有 null，否则前端下拉会渲染出空白项");
        }
        List<String> basins = dao.listBasins();
        assertNotNull(basins);
    }

    @Test
    @DisplayName("countAll / countByStatus / countArchived：统计口径")
    void counts() {
        assertEquals(dao.listAll().size(), dao.countAll());
        assertEquals(dao.listByStatus("ACTIVE").size(), dao.countByStatus("ACTIVE"));
        assertTrue(dao.countArchived() >= 0);
    }

    /* ================================================================== */
    /*  写（自建自删）                                                      */
    /* ================================================================== */

    @Test
    @DisplayName("insert → findById → update → delete 全链路")
    void insertUpdateDeleteRoundTrip() {
        Typhoon t = new Typhoon();
        t.setInternationalId("UT0000009");
        t.setChineseName("单元测试用台风");
        t.setEnglishName("UnitTestTyphoon");
        t.setBasin("西北太平洋");
        t.setStatus("ACTIVE");
        t.setGenerateTime(LocalDateTime.of(2026, 9, 13, 8, 0, 0));

        long id = dao.insert(t);
        try {
            assertTrue(id > 0L, "insert 必须回填自增主键");
            Typhoon saved = dao.findById(id);
            assertNotNull(saved);
            assertEquals("单元测试用台风", saved.getChineseName());

            saved.setStatus("FINISHED");
            assertEquals(1, dao.update(saved), "更新一条应影响 1 行");
            assertEquals("FINISHED", dao.findById(id).getStatus());

            // 极值刷新与归档：即便没有轨迹数据也应返回 0 而不是报错
            assertTrue(dao.refreshIntensityExtremes(id) >= 0);
            assertEquals(1, dao.archive(id, LocalDateTime.of(2026, 9, 20, 8, 0, 0)));
        } finally {
            dao.deleteById(id);
        }
        assertNull(dao.findById(id), "删除后应查不到");
    }

    @Test
    @DisplayName("删除不存在的记录返回 0，不抛异常")
    void deleteMissingReturnsZero() {
        assertEquals(0, dao.deleteById(-999L));
    }
}
