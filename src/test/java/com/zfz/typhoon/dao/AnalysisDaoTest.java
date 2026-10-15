package com.zfz.typhoon.dao;

import com.zfz.typhoon.dao.impl.AiPredictionRecordDaoImpl;
import com.zfz.typhoon.dao.impl.ExportRecordDaoImpl;
import com.zfz.typhoon.dao.impl.HistoricalComparisonDaoImpl;
import com.zfz.typhoon.dao.impl.RiskAssessmentDaoImpl;
import com.zfz.typhoon.dao.impl.SandboxScenarioDaoImpl;
import com.zfz.typhoon.dao.impl.SimilarCaseMatchDaoImpl;
import com.zfz.typhoon.model.AiPredictionRecord;
import com.zfz.typhoon.model.ExportRecord;
import com.zfz.typhoon.model.HistoricalComparison;
import com.zfz.typhoon.model.RiskAssessment;
import com.zfz.typhoon.model.SandboxScenario;
import com.zfz.typhoon.model.SimilarCaseMatch;
import com.zfz.typhoon.util.DBUtil;
import com.zfz.typhoon.util.JdbcTemplate;
import org.junit.jupiter.api.AfterAll;
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
 * 分析类 DAO 的集成测试：风险评估、相似案例、历史对比、沙盘、AI 预测、导出记录。
 *
 * <p>这几个表的共同特点是<b>结果是算出来的、会被反复覆盖</b>，
 * 所以重点验证两件事：
 * <ol>
 *   <li>"取最新一条"的语义（{@code findLatest} / {@code listLatestPerModel}）——
 *       取错版本的后果是页面上展示过期结论；</li>
 *   <li>upsert 的幂等性（重算一次不能产生重复行）。</li>
 * </ol>
 *
 * @author 追风者项目组
 */
@DisplayName("分析类 DAO 集成测试")
class AnalysisDaoTest {

    private static RiskAssessmentDao riskDao;
    private static SimilarCaseMatchDao similarDao;
    private static HistoricalComparisonDao comparisonDao;
    private static SandboxScenarioDao sandboxDao;
    private static AiPredictionRecordDao aiDao;
    private static ExportRecordDao exportDao;

    private static long sampleTyphoonId;
    private static long sampleCityId;
    private static long sampleUserId;
    private static long scratchTyphoonId;
    private static final String SCRATCH_CODE = "UT0000006";

    @BeforeAll
    static void setUp() {
        Assumptions.assumeTrue(DBUtil.isAvailable(),
                "本机数据库不可用（" + DBUtil.getInitError() + "），跳过");
        riskDao = new RiskAssessmentDaoImpl();
        similarDao = new SimilarCaseMatchDaoImpl();
        comparisonDao = new HistoricalComparisonDaoImpl();
        sandboxDao = new SandboxScenarioDaoImpl();
        aiDao = new AiPredictionRecordDaoImpl();
        exportDao = new ExportRecordDaoImpl();

        Long t = JdbcTemplate.queryLong("SELECT MIN(typhoon_id) FROM typhoons");
        sampleTyphoonId = t == null ? 0L : t.longValue();
        Long c = JdbcTemplate.queryLong("SELECT MIN(city_id) FROM cities");
        sampleCityId = c == null ? 0L : c.longValue();
        Long u = JdbcTemplate.queryLong("SELECT MIN(user_id) FROM users");
        sampleUserId = u == null ? 0L : u.longValue();

        JdbcTemplate.update("DELETE FROM typhoons WHERE international_id = ?", SCRATCH_CODE);
        scratchTyphoonId = JdbcTemplate.insert("INSERT INTO typhoons"
                + " (international_id, chinese_name, english_name, basin, status, generate_time)"
                + " VALUES (?, ?, ?, ?, ?, ?)",
                SCRATCH_CODE, "分析测试用台风", "AnalysisTestTyphoon", "西北太平洋", "ACTIVE",
                LocalDateTime.of(2026, 9, 13, 8, 0, 0)).longValue();
    }

    @AfterAll
    static void tearDown() {
        if (!DBUtil.isAvailable()) {
            return;
        }
        try {
            riskDao.deleteByTyphoon(scratchTyphoonId);
            similarDao.deleteByCurrentTyphoon(scratchTyphoonId);
            aiDao.deleteByTyphoon(scratchTyphoonId);
            JdbcTemplate.update("DELETE FROM typhoons WHERE typhoon_id = ?",
                    Long.valueOf(scratchTyphoonId));
        } catch (RuntimeException ignored) {
            // 清理失败不掩盖测试结论
        }
    }

    @Nested
    @DisplayName("RiskAssessmentDaoImpl")
    class Risk {

        @Test
        @DisplayName("findLatest / findLatestByUser / listLatestByTyphoon：无数据时安全返回")
        void readsAreNullSafe() {
            assertTrue(riskDao.findLatest(-999L, -999L) == null);
            assertTrue(riskDao.findLatestByUser(-999L, -999L, -999L) == null);
            assertTrue(riskDao.listLatestByTyphoon(-999L).isEmpty());
            assertTrue(riskDao.listAssessedCityIds(-999L).isEmpty());
            assertTrue(riskDao.listByUser(sampleUserId, 10) != null);
        }

        @Test
        @DisplayName("insert → 取最新 → 覆盖 → 删除")
        void writeRoundTrip() {
            try {
                RiskAssessment r = new RiskAssessment();
                r.setTyphoonId(Long.valueOf(scratchTyphoonId));
                r.setCityId(Long.valueOf(sampleCityId));
                r.setUserId(Long.valueOf(sampleUserId));
                r.setAssessmentTime(LocalDateTime.of(2026, 9, 13, 10, 0, 0));
                r.setDistanceKm(Double.valueOf(120D));
                r.setRiskLevel("HIGH");
                r.setRiskScore(Double.valueOf(78D));
                r.setSuggestion("请做好防御准备");

                long id = riskDao.insert(r);
                assertTrue(id > 0L);

                RiskAssessment latest = riskDao.findLatest(scratchTyphoonId, sampleCityId);
                assertNotNull(latest);
                assertEquals("HIGH", latest.getRiskLevel());
                assertNotNull(riskDao.findLatestByUser(scratchTyphoonId, sampleCityId, sampleUserId));
                assertEquals(1, riskDao.listLatestByTyphoon(scratchTyphoonId).size());
                assertTrue(riskDao.listAssessedCityIds(scratchTyphoonId)
                        .contains(Long.valueOf(sampleCityId)));

                latest.setRiskScore(Double.valueOf(55D));
                latest.setRiskLevel("MEDIUM");
                assertEquals(1, riskDao.update(latest));
                assertEquals("MEDIUM",
                        riskDao.findLatest(scratchTyphoonId, sampleCityId).getRiskLevel());
            } finally {
                riskDao.deleteByTyphoon(scratchTyphoonId);
            }
            assertTrue(riskDao.listLatestByTyphoon(scratchTyphoonId).isEmpty());
        }
    }

    @Nested
    @DisplayName("SimilarCaseMatchDaoImpl")
    class Similar {

        @Test
        @DisplayName("无匹配记录时返回空列表而不是报错")
        void readsAreNullSafe() {
            assertTrue(similarDao.listByCurrentTyphoon(-999L).isEmpty());
            assertTrue(similarDao.listTopMatches(-999L, 5).isEmpty());
            assertTrue(similarDao.findByPair(-999L, -998L) == null);
        }

        @Test
        @DisplayName("insert → upsert（幂等）→ 批量 upsert → 删除")
        void writeRoundTrip() {
            try {
                SimilarCaseMatch m = new SimilarCaseMatch();
                m.setCurrentTyphoonId(Long.valueOf(scratchTyphoonId));
                m.setHistoryTyphoonId(Long.valueOf(sampleTyphoonId));
                m.setSimilarityScore(Double.valueOf(0.82D));
                m.setPathSimilarity(Double.valueOf(0.9D));
                m.setPeakIntensitySimilarity(Double.valueOf(0.8D));
                m.setSeasonSimilarity(Double.valueOf(0.7D));
                m.setMoveSpeedSimilarity(Double.valueOf(0.75D));
                m.setWindCircleSimilarity(Double.valueOf(0.6D));

                long id = similarDao.insert(m);
                assertTrue(id > 0L);

                assertNotNull(similarDao.findByPair(scratchTyphoonId, sampleTyphoonId));
                assertEquals(1, similarDao.listByCurrentTyphoon(scratchTyphoonId).size());
                assertEquals(1, similarDao.listTopMatches(scratchTyphoonId, 5).size());
                assertFalse(similarDao.listByHistoryTyphoon(sampleTyphoonId).isEmpty());

                m.setSimilarityScore(Double.valueOf(0.9D));
                assertTrue(similarDao.upsert(m) >= 0);
                assertTrue(similarDao.upsertAll(
                        java.util.Collections.singletonList(m)) >= 0);
                assertEquals(1, similarDao.listByCurrentTyphoon(scratchTyphoonId).size(),
                        "upsert 必须是更新而不是新增，否则重算一次会多出一堆重复案例");
            } finally {
                similarDao.deleteByCurrentTyphoon(scratchTyphoonId);
            }
        }
    }

    @Nested
    @DisplayName("HistoricalComparisonDaoImpl")
    class Comparison {

        @Test
        @DisplayName("他人看不到别人的对比任务")
        void readsAreScoped() {
            assertTrue(comparisonDao.listByUser(-999L).isEmpty());
            assertEquals(0, comparisonDao.countByUser(-999L));
            assertTrue(comparisonDao.findById(-999L) == null);
        }

        @Test
        @DisplayName("insert → 改名 → 写结果 → 按用户删除")
        void writeRoundTrip() {
            HistoricalComparison h = new HistoricalComparison();
            h.setUserId(Long.valueOf(sampleUserId));
            h.setComparisonName("单元测试对比");
            h.setPrimaryTyphoonId(Long.valueOf(sampleTyphoonId));
            long id = comparisonDao.insert(h);
            try {
                assertNotNull(comparisonDao.findById(id));
                assertFalse(comparisonDao.listByUser(sampleUserId).isEmpty());
                assertFalse(comparisonDao.listByPrimaryTyphoon(sampleTyphoonId).isEmpty());

                assertEquals(1, comparisonDao.rename(id, "改过的名字", sampleUserId));
                assertEquals("改过的名字", comparisonDao.findById(id).getComparisonName());
                assertEquals(0, comparisonDao.rename(id, "越权改名", -999L),
                        "userId 必须在 WHERE 里，否则任何人都能改别人的任务名");

                assertEquals(1, comparisonDao.updateResult(id, "{\"summary\":\"ok\"}"));

                assertTrue(comparisonDao.countByUser(sampleUserId) >= 1);
                assertEquals(1, comparisonDao.deleteByIdAndUser(id, sampleUserId));
                assertEquals(0, comparisonDao.deleteByIdAndUser(id, sampleUserId));
            } finally {
                comparisonDao.deleteByIdAndUser(id, sampleUserId);
            }
        }
    }

    @Nested
    @DisplayName("SandboxScenarioDaoImpl")
    class Sandbox {

        @Test
        @DisplayName("只读查询：列表 / 公开场景 / 计数")
        void reads() {
            assertNotNull(sandboxDao.listByUser(sampleUserId));
            // listVisible 取代了原先的 listPublic / listByBaseTyphoon：
            // 前者只查 is_public=1，后者会把他人未公开的方案也捞出来（越权）。
            assertNotNull(sandboxDao.listVisible(null, null, 10));
            assertNotNull(sandboxDao.listVisible(Long.valueOf(sampleUserId), null, 10));
            assertNotNull(sandboxDao.listVisible(Long.valueOf(sampleUserId), Long.valueOf(sampleTyphoonId), 10));
            assertTrue(sandboxDao.countByUser(sampleUserId) >= 0);
            assertTrue(sandboxDao.countAll() >= 0);
            assertTrue(sandboxDao.findById(-999L) == null);
        }

        /**
         * 可见性语义的真库验证。仅断言 {@code listVisible} 非 null 是分辨不出越权的
         * （去掉 WHERE 也能返回非 null），因此这里插入一条<b>私有</b>方案再逐条分辨。
         */
        @Test
        @DisplayName("可见性：自己的私有方案只对自己可见，且 baseTyphoonId 过滤不被 OR 绕过")
        void visibilityIsolatesPrivateScenarios() {
            SandboxScenario s = new SandboxScenario();
            s.setUserId(Long.valueOf(sampleUserId));
            s.setBaseTyphoonId(Long.valueOf(sampleTyphoonId));
            s.setScenarioName("可见性用例-私有");
            s.setScenarioType(SandboxScenario.TYPE_BASELINE);
            s.setParamsJson(com.zfz.typhoon.util.JsonUtil.parse(
                    "{}", com.fasterxml.jackson.databind.JsonNode.class));
            s.setResultJson(com.zfz.typhoon.util.JsonUtil.parse(
                    "{}", com.fasterxml.jackson.databind.JsonNode.class));
            s.setPublicShared(Boolean.FALSE);
            long id = sandboxDao.insert(s);
            try {
                assertTrue(containsId(sandboxDao.listVisible(Long.valueOf(sampleUserId), null, 200), id),
                        "自己存的私有方案必须在默认口径里，否则用户存完就找不到了");
                assertFalse(containsId(sandboxDao.listVisible(null, null, 200), id),
                        "匿名请求绝不能看到未公开方案（越权）");

                // 括号验证：换一个不存在的基底台风去查。
                //  正确 SQL —— base_typhoon_id = ? AND (is_public = 1 OR user_id = ?) → 不匹配
                //  丢掉括号 —— base_typhoon_id = ? AND is_public = 1 OR user_id = ? → 被 user_id 分支放行
                assertFalse(containsId(sandboxDao.listVisible(Long.valueOf(sampleUserId),
                                Long.valueOf(sampleTyphoonId + 99999L), 200), id),
                        "baseTyphoonId 过滤必须仍然生效：丢了括号 OR 会绕过它");

                // 公开之后就轮到 is_public 分支生效，匿名也能看到
                assertEquals(1, sandboxDao.setPublic(id, true));
                assertTrue(containsId(sandboxDao.listVisible(null, null, 200), id),
                        "公开后匿名应能看到");
            } finally {
                sandboxDao.deleteById(id);
            }
        }

        /**
         * 判断结果集里是否含指定情景ID。
         *
         * @param rows 结果集
         * @param id   情景ID
         * @return 含则返回 true
         */
        private boolean containsId(List<SandboxScenario> rows, long id) {
            for (SandboxScenario r : rows) {
                if (r.getScenarioId() != null && r.getScenarioId().longValue() == id) {
                    return true;
                }
            }
            return false;
        }

        @Test
        @DisplayName("insert → 改名改参 → 公开/取消公开 → 删除")
        void writeRoundTrip() {
            SandboxScenario s = new SandboxScenario();
            s.setUserId(Long.valueOf(sampleUserId));
            s.setBaseTyphoonId(Long.valueOf(sampleTyphoonId));
            s.setScenarioName("单元测试沙盘");
            s.setScenarioType("INTENSITY");
            // params_json / result_json 是 NOT NULL 的 JSON 列，必须给值
            s.setParamsJson(com.zfz.typhoon.util.JsonUtil.parse(
                    "{\"windAdjust\":0}", com.fasterxml.jackson.databind.JsonNode.class));
            s.setResultJson(com.zfz.typhoon.util.JsonUtil.parse(
                    "{\"riskScore\":0}", com.fasterxml.jackson.databind.JsonNode.class));
            s.setPublicShared(Boolean.FALSE);
            long id = sandboxDao.insert(s);
            try {
                assertNotNull(sandboxDao.findById(id));
                assertFalse(sandboxDao.listByUserAndTyphoon(sampleUserId, sampleTyphoonId).isEmpty());

                SandboxScenario saved = sandboxDao.findById(id);
                saved.setScenarioName("改过的沙盘");
                assertEquals(1, sandboxDao.update(saved));
                assertEquals("改过的沙盘", sandboxDao.findById(id).getScenarioName());

                assertEquals(1, sandboxDao.setPublic(id, true));
                assertEquals(Boolean.TRUE, sandboxDao.findById(id).getPublicShared());
                assertEquals(1, sandboxDao.setPublic(id, false));
            } finally {
                sandboxDao.deleteById(id);
            }
            assertTrue(sandboxDao.findById(id) == null);
        }
    }

    @Nested
    @DisplayName("AiPredictionRecordDaoImpl")
    class AiPrediction {

        @Test
        @DisplayName("无预测记录时安全返回")
        void readsAreNullSafe() {
            assertTrue(aiDao.listLatestPerModel(-999L).isEmpty());
            assertTrue(aiDao.findLatest(-999L) == null);
            assertEquals(0, aiDao.countByTyphoon(-999L));
            assertTrue(aiDao.findLatestPredictionTime(-999L) == null);
        }

        @Test
        @DisplayName("多模型各取最新一条：同一模型不能出现两条")
        void latestPerModelDeduplicates() {
            try {
                // 同一模型跑两次（时刻不同），验证"只保留最新一次"
                String[] models = {"MODEL_A", "MODEL_B", "MODEL_A"};
                LocalDateTime[] times = {
                        LocalDateTime.of(2026, 9, 13, 11, 0, 0),
                        LocalDateTime.of(2026, 9, 13, 11, 0, 0),
                        LocalDateTime.of(2026, 9, 13, 12, 0, 0)};
                for (int i = 0; i < models.length; i++) {
                    AiPredictionRecord a = new AiPredictionRecord();
                    a.setTyphoonId(Long.valueOf(scratchTyphoonId));
                    a.setModelName(models[i]);
                    a.setPromptVersion("v1");
                    a.setPredictionTime(times[i]);
                    a.setConfidenceScore(Double.valueOf(0.72D));
                    a.setStatus("SUCCESS");
                    aiDao.insert(a);
                }
                List<AiPredictionRecord> latest = aiDao.listLatestPerModel(scratchTyphoonId);
                assertEquals(2, latest.size(), "两个模型应各只留最新一次预测");
                java.util.Set<String> seenModels = new java.util.HashSet<String>();
                for (AiPredictionRecord a : latest) {
                    assertTrue(seenModels.add(a.getModelName()), "同一模型出现了两条");
                }
                assertNotNull(aiDao.findLatest(scratchTyphoonId));
                assertEquals(3, aiDao.countByTyphoon(scratchTyphoonId));
                assertNotNull(aiDao.findLatestPredictionTime(scratchTyphoonId));
                assertFalse(aiDao.listByTyphoon(scratchTyphoonId).isEmpty());
                assertFalse(aiDao.listByTyphoonAndModel(scratchTyphoonId, "MODEL_A").isEmpty());
                assertEquals(1, aiDao.updateStatus(
                        aiDao.findLatest(scratchTyphoonId).getRecordId().longValue(),
                        "ARCHIVED", Double.valueOf(3.5D)));
            } finally {
                aiDao.deleteByTyphoon(scratchTyphoonId);
            }
        }
    }

    @Nested
    @DisplayName("ExportRecordDaoImpl")
    class Export {

        @Test
        @DisplayName("无导出记录时安全返回")
        void readsAreNullSafe() {
            assertTrue(exportDao.listByUser(-999L, 10).isEmpty());
            assertEquals(0, exportDao.countByUser(-999L));
            assertEquals(0, exportDao.countTodayByUser(-999L));
            assertTrue(exportDao.findById(-999L) == null);
        }

        @Test
        @DisplayName("insert → 按用户/台风回查 → 按用户删除（别人的删不掉）")
        void writeRoundTrip() {
            ExportRecord e = new ExportRecord();
            e.setUserId(Long.valueOf(sampleUserId));
            e.setTyphoonId(Long.valueOf(sampleTyphoonId));
            e.setExportType("CSV");
            e.setFileUrl("/export/ut-test.csv");
            e.setFileSize(Integer.valueOf(1024));
            long id = exportDao.insert(e);
            try {
                assertNotNull(exportDao.findById(id));
                assertFalse(exportDao.listByUser(sampleUserId, 10).isEmpty());
                assertFalse(exportDao.listByTyphoon(sampleTyphoonId, 10).isEmpty());
                assertFalse(exportDao.listByUserAndType(sampleUserId, "CSV").isEmpty());
                assertTrue(exportDao.countTodayByUser(sampleUserId) >= 1);
                assertEquals(0, exportDao.deleteByIdAndUser(id, -999L),
                        "删除接口必须带 userId，否则能删别人的导出记录");
            } finally {
                assertEquals(1, exportDao.deleteByIdAndUser(id, sampleUserId));
            }
        }

        @Test
        @DisplayName("文件名时间戳格式可用于文件名（不含冒号空格）")
        void timestampForFileName() {
            String ts = ExportRecordDaoImpl.timestampForFileName();
            assertNotNull(ts);
            assertFalse(ts.contains(":"), "文件名里不能有冒号（Windows 不允许）：" + ts);
            assertFalse(ts.contains(" "), "文件名里不能有空格：" + ts);
        }
    }
}
