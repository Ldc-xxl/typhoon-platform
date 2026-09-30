package com.zfz.typhoon.dao;

import com.zfz.typhoon.dao.impl.ForecastAgencyDaoImpl;
import com.zfz.typhoon.dao.impl.ForecastDisagreeRecordDaoImpl;
import com.zfz.typhoon.dao.impl.ForecastPathDaoImpl;
import com.zfz.typhoon.model.ForecastAgency;
import com.zfz.typhoon.model.ForecastPath;
import com.zfz.typhoon.util.DBUtil;
import com.zfz.typhoon.util.JdbcTemplate;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 预报路径 / 预报机构 / 分歧记录 DAO 的集成测试。
 *
 * <p>"多机构预报对比"是本平台的核心卖点，三条数据之间的关系是：
 * 机构（维度）→ 路径（事实）→ 分歧记录（分析结论）。
 * 这里重点验证 <b>listLatestByTyphoon 的"各机构只取最新一批"语义</b>——
 * 这个 SQL 写错的表现是"图上多出几条旧线"，肉眼极难发现。
 *
 * @author 追风者项目组
 */
@DisplayName("预报数据 DAO 集成测试")
class ForecastDaoTest {

    private static ForecastPathDao pathDao;
    private static ForecastAgencyDao agencyDao;
    private static ForecastDisagreeRecordDao disagreeDao;
    private static long sampleTyphoonId;
    private static long sampleAgencyId;
    private static String sampleAgencyCode;

    @BeforeAll
    static void setUp() {
        Assumptions.assumeTrue(DBUtil.isAvailable(),
                "本机数据库不可用（" + DBUtil.getInitError() + "），跳过");
        pathDao = new ForecastPathDaoImpl();
        agencyDao = new ForecastAgencyDaoImpl();
        disagreeDao = new ForecastDisagreeRecordDaoImpl();
        Long t = JdbcTemplate.queryLong("SELECT typhoon_id FROM forecast_paths ORDER BY typhoon_id LIMIT 1");
        sampleTyphoonId = t == null ? 0L : t.longValue();
        Long a = JdbcTemplate.queryLong("SELECT MIN(agency_id) FROM forecast_agencies");
        sampleAgencyId = a == null ? 0L : a.longValue();
        sampleAgencyCode = JdbcTemplate.queryString(
                "SELECT agency_code FROM forecast_agencies WHERE agency_id = ?",
                Long.valueOf(sampleAgencyId));
    }

    @Nested
    @DisplayName("ForecastAgencyDaoImpl")
    class Agencies {

        @Test
        @DisplayName("listAll / listEnabled：机构清单")
        void lists() {
            List<ForecastAgency> all = agencyDao.listAll();
            assertNotNull(all);
            assertFalse(all.isEmpty(), "演示库里应有预报机构");
            for (ForecastAgency a : all) {
                assertNotNull(a.getAgencyId());
                assertNotNull(a.getAgencyCode(), "机构编码是聚合分组的键，不能为空");
                assertNotNull(a.getDisplayColor(), "前端按机构配色画线，缺了会画成黑色");
            }
            assertNotNull(agencyDao.listEnabled());
        }

        @Test
        @DisplayName("findById / findByCode / listByCodes")
        void lookups() {
            ForecastAgency byId = agencyDao.findById(sampleAgencyId);
            assertNotNull(byId);
            assertEquals(Long.valueOf(sampleAgencyId), byId.getAgencyId());
            if (sampleAgencyCode != null) {
                assertNotNull(agencyDao.findByCode(sampleAgencyCode));
                assertNotNull(agencyDao.listByCodes(
                        java.util.Arrays.asList(sampleAgencyCode, "NO-SUCH-CODE")));
            }
            assertTrue(agencyDao.findById(-999L) == null);
        }
    }

    @Nested
    @DisplayName("ForecastPathDaoImpl")
    class Paths {

        @Test
        @DisplayName("listByTyphoon：返回该台风的全部预报点")
        void listByTyphoon() {
            List<ForecastPath> rows = pathDao.listByTyphoon(sampleTyphoonId);
            assertNotNull(rows);
            assertFalse(rows.isEmpty());
            for (ForecastPath p : rows) {
                assertNotNull(p.getForecastTime(), "预报时刻缺失会导致「过滤已过时点」逻辑失效");
                assertNotNull(p.getLatitude());
                assertNotNull(p.getLongitude());
            }
        }

        @Test
        @DisplayName("listLatestByTyphoon：同一机构的点必须是同一批发布的")
        void latestPerAgencyIsOneBatch() {
            List<ForecastPath> rows = pathDao.listLatestByTyphoon(sampleTyphoonId);
            assertNotNull(rows);
            java.util.Map<String, java.util.Set<String>> publishOfAgency =
                    new java.util.HashMap<String, java.util.Set<String>>();
            for (ForecastPath p : rows) {
                String code = p.getAgencyCode() == null ? "-" : p.getAgencyCode();
                java.util.Set<String> set = publishOfAgency.get(code);
                if (set == null) {
                    set = new java.util.HashSet<String>();
                    publishOfAgency.put(code, set);
                }
                if (p.getPublishTime() != null) {
                    set.add(p.getPublishTime().toString());
                }
            }
            for (java.util.Map.Entry<String, java.util.Set<String>> e : publishOfAgency.entrySet()) {
                assertTrue(e.getValue().size() <= 1,
                        "机构 " + e.getKey() + " 混入了 " + e.getValue().size()
                                + " 个发布批次，图上会出现同一机构的两条线");
            }
        }

        @Test
        @DisplayName("listLatestByTyphoonAndAgency：只保留指定机构")
        void latestByAgency() {
            List<ForecastPath> rows = pathDao.listLatestByTyphoonAndAgency(
                    sampleTyphoonId, sampleAgencyCode == null ? "-" : sampleAgencyCode);
            assertNotNull(rows);
            for (ForecastPath p : rows) {
                assertEquals(sampleAgencyCode, p.getAgencyCode());
            }
        }

        @Test
        @DisplayName("发布时间查询：最新 / 最早一批")
        void publishTimeLookups() {
            LocalDateTime latest = pathDao.findLatestPublishTime(sampleTyphoonId, sampleAgencyCode);
            LocalDateTime oldest = pathDao.findOldestLatestPublishTime(sampleTyphoonId);
            if (latest != null && oldest != null) {
                assertFalse(oldest.isAfter(latest),
                        "最早一批不应晚于最新一批");
            }
            assertTrue(pathDao.findLatestPublishTime(-999L, "NO-SUCH") == null);
        }

        @Test
        @DisplayName("countByTyphoon 与 listByTyphoon 行数一致")
        void countMatchesList() {
            assertEquals(pathDao.listByTyphoon(sampleTyphoonId).size(),
                    pathDao.countByTyphoon(sampleTyphoonId));
        }
    }

    @Nested
    @DisplayName("ForecastDisagreeRecordDaoImpl")
    class Disagreement {

        @Test
        @DisplayName("findLatestByTyphoon / listByTyphoon：无数据时返回 null / 空列表")
        void readsAreNullSafe() {
            assertNotNull(disagreeDao.listByTyphoon(sampleTyphoonId));
            assertTrue(disagreeDao.findLatestByTyphoon(-999L) == null,
                    "没有分歧记录不是错误，前端会展示「各机构结论一致」");
        }

        @Test
        @DisplayName("findByTyphoonAndTime / listHighUncertainty：按时刻与不确定性查询")
        void moreLookups() {
            assertTrue(disagreeDao.findByTyphoonAndTime(sampleTyphoonId,
                    LocalDateTime.of(1999, 1, 1, 0, 0)) == null);
            assertNotNull(disagreeDao.listHighUncertainty());
        }
    }
}
