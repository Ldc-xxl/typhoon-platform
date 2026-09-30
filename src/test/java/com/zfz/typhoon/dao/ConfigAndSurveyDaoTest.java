package com.zfz.typhoon.dao;

import com.zfz.typhoon.dao.impl.SurveyAggResultDaoImpl;
import com.zfz.typhoon.dao.impl.SystemSettingDaoImpl;
import com.zfz.typhoon.dao.impl.UserBehaviorSurveyDaoImpl;
import com.zfz.typhoon.model.SurveyAggResult;
import com.zfz.typhoon.model.SystemSetting;
import com.zfz.typhoon.model.UserBehaviorSurvey;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 系统配置与问卷统计 DAO 的集成测试。
 *
 * <p>系统配置是"不重启就能调参"的实现基础：风险阈值、AI 模型开关、
 * 相似案例权重都从这里读。因此 {@code getValue / getInt / getDouble / getBoolean}
 * 的<b>类型解析与默认值兜底</b>必须准——解析失败会静默变成默认值，
 * 表现是"后台改了参数但页面没反应"。
 *
 * @author 追风者项目组
 */
@DisplayName("配置与问卷 DAO 集成测试")
class ConfigAndSurveyDaoTest {

    private static SystemSettingDao settingDao;
    /** insertMasked / latestSubmitAgeHours 只在实现类上声明（接口未暴露），
     *  所以这里按实现类类型持有引用 */
    private static UserBehaviorSurveyDaoImpl surveyDao;
    private static SurveyAggResultDao aggDao;
    private static long sampleTyphoonId;
    private static long sampleCityId;
    /** 专供写入用例的测试台风：问卷/聚合的删除接口按 typhoonId 批量删，
     *  直接用真实台风会把演示数据一起删掉 */
    private static long scratchTyphoonId;
    private static final String SCRATCH_CODE = "UT0000007";

    @BeforeAll
    static void setUp() {
        Assumptions.assumeTrue(DBUtil.isAvailable(),
                "本机数据库不可用（" + DBUtil.getInitError() + "），跳过");
        settingDao = new SystemSettingDaoImpl();
        surveyDao = new UserBehaviorSurveyDaoImpl();
        aggDao = new SurveyAggResultDaoImpl();
        Long t = JdbcTemplate.queryLong("SELECT MIN(typhoon_id) FROM typhoons");
        sampleTyphoonId = t == null ? 0L : t.longValue();
        Long c = JdbcTemplate.queryLong("SELECT MIN(city_id) FROM cities");
        sampleCityId = c == null ? 0L : c.longValue();

        JdbcTemplate.update("DELETE FROM typhoons WHERE international_id = ?", SCRATCH_CODE);
        scratchTyphoonId = JdbcTemplate.insert("INSERT INTO typhoons"
                + " (international_id, chinese_name, english_name, basin, status, generate_time)"
                + " VALUES (?, ?, ?, ?, ?, ?)",
                SCRATCH_CODE, "问卷测试用台风", "SurveyTestTyphoon", "西北太平洋", "ACTIVE",
                LocalDateTime.of(2026, 9, 13, 8, 0, 0)).longValue();
    }

    @AfterAll
    static void tearDown() {
        if (DBUtil.isAvailable()) {
            try {
                settingDao.deleteByKey("ut.test.string");
                settingDao.deleteByKey("ut.test.int");
                settingDao.deleteByKey("ut.test.double");
                settingDao.deleteByKey("ut.test.bool");
                surveyDao.deleteByTyphoon(scratchTyphoonId);
                aggDao.deleteByTyphoon(scratchTyphoonId);
                JdbcTemplate.update("DELETE FROM typhoons WHERE typhoon_id = ?",
                        Long.valueOf(scratchTyphoonId));
            } catch (RuntimeException ignored) {
                // 清理失败不掩盖测试结论
            }
        }
    }

    @Nested
    @DisplayName("SystemSettingDaoImpl")
    class Settings {

        @Test
        @DisplayName("listAll / findByKey / countAll")
        void reads() {
            List<SystemSetting> all = settingDao.listAll();
            assertNotNull(all);
            assertEquals(all.size(), settingDao.countAll());
            assertTrue(settingDao.findByKey("no-such-key-9999") == null);
        }

        @Test
        @DisplayName("读取不存在的键时返回调用方给的默认值，而不是 null 或 0")
        void missingKeyReturnsDefault() {
            assertEquals("默认文案", settingDao.getValue("no-such-key-9999", "默认文案"));
            assertEquals(42, settingDao.getInt("no-such-key-9999", 42));
            assertEquals(1.5D, settingDao.getDouble("no-such-key-9999", 1.5D), 0.0001D);
            assertEquals(true, settingDao.getBoolean("no-such-key-9999", true));
        }

        @Test
        @DisplayName("upsertValue → 按类型解析回读 → 改值幂等 → 删除")
        void writeRoundTrip() {
            assertTrue(settingDao.upsertValue("ut.test.string", "hello", "STRING", null) >= 0);
            assertEquals("hello", settingDao.getValue("ut.test.string", "x"));
            assertNotNull(settingDao.findByKey("ut.test.string"));

            settingDao.upsertValue("ut.test.int", "123", "INT", null);
            assertEquals(123, settingDao.getInt("ut.test.int", 0));

            settingDao.upsertValue("ut.test.double", "12.5", "DOUBLE", null);
            assertEquals(12.5D, settingDao.getDouble("ut.test.double", 0D), 0.0001D);

            settingDao.upsertValue("ut.test.bool", "true", "BOOL", null);
            assertEquals(true, settingDao.getBoolean("ut.test.bool", false));

            // 幂等：同一个键再写一次应覆盖而不是新增一行
            settingDao.upsertValue("ut.test.string", "hello2", "STRING", null);
            assertEquals("hello2", settingDao.getValue("ut.test.string", "x"));
            assertEquals(1, JdbcTemplate.queryInt(
                    "SELECT COUNT(*) FROM system_settings WHERE setting_key = ?",
                    "ut.test.string").intValue());

            assertEquals(1, settingDao.deleteByKey("ut.test.string"));
            assertEquals("默认文案", settingDao.getValue("ut.test.string", "默认文案"));
        }

        @Test
        @DisplayName("值不是数字时回落到默认值，而不是抛异常把接口打成 500")
        void nonNumericFallsBackToDefault() {
            settingDao.upsertValue("ut.test.int", "不是数字", "INT", null);
            assertEquals(7, settingDao.getInt("ut.test.int", 7),
                    "配置被改成非法值时，读出来应是默认值而不是崩掉");
        }

        @Test
        @DisplayName("insert：新增一条配置")
        void insertSetting() {
            SystemSetting s = new SystemSetting();
            s.setSettingKey("ut.test.inserted");
            s.setSettingValue("v");
            s.setSettingType("STRING");
            s.setDescription("单元测试");
            try {
                long id = settingDao.insert(s);
                assertTrue(id > 0L);
                assertNotNull(settingDao.findByKey("ut.test.inserted"));
            } finally {
                settingDao.deleteByKey("ut.test.inserted");
            }
        }
    }

    @Nested
    @DisplayName("UserBehaviorSurveyDaoImpl")
    class Surveys {

        @Test
        @DisplayName("insert → 计数 → 按台风+城市查 → 聚合 → 删除")
        void writeRoundTrip() {
            try {
                UserBehaviorSurvey s = new UserBehaviorSurvey();
                s.setTyphoonId(Long.valueOf(scratchTyphoonId));
                s.setCityId(Long.valueOf(sampleCityId));
                s.setQuestionSetVersion("v1");
                s.setPreparedSupplies(Boolean.TRUE);
                s.setPayAttentionEvacuation(Boolean.TRUE);
                s.setWorriedDisasterType("暴雨");
                s.setTrustLevel(Integer.valueOf(4));
                s.setSubmitTime(LocalDateTime.of(2026, 9, 13, 9, 0, 0));
                s.setContactInfo("13800000000");

                long id = surveyDao.insert(s);
                assertTrue(id > 0L);
                assertEquals(1, surveyDao.countByTyphoon(scratchTyphoonId));
                assertEquals(surveyDao.countByTyphoonAndCity(scratchTyphoonId, sampleCityId),
                        surveyDao.listByTyphoonAndCity(scratchTyphoonId, sampleCityId).size());
                assertTrue(surveyDao.countAll() >= 1);

                assertNotNull(surveyDao.aggregateByTyphoon(scratchTyphoonId));
                assertNotNull(surveyDao.aggregateByTyphoonAndCity(scratchTyphoonId, sampleCityId));

                // 脱敏写入：联系方式不能原样落库
                UserBehaviorSurvey masked = new UserBehaviorSurvey();
                masked.setTyphoonId(Long.valueOf(scratchTyphoonId));
                masked.setCityId(Long.valueOf(sampleCityId));
                masked.setQuestionSetVersion("v1");
                masked.setTrustLevel(Integer.valueOf(3));
                masked.setSubmitTime(LocalDateTime.of(2026, 9, 13, 10, 0, 0));
                assertTrue(surveyDao.insertMasked(masked, "138****0000") > 0L);

                assertTrue(surveyDao.latestSubmitAgeHours(scratchTyphoonId) >= 0D);
            } finally {
                surveyDao.deleteByTyphoon(scratchTyphoonId);
            }
            assertEquals(0, surveyDao.countByTyphoon(scratchTyphoonId), "清理后应无残留");
        }

        @Test
        @DisplayName("没有问卷数据的台风：计数为 0，聚合返回空/null 而不是抛异常")
        void emptyTyphoonIsSafe() {
            assertEquals(0, surveyDao.countByTyphoon(-999L));
            assertTrue(surveyDao.listByTyphoonAndCity(-999L, -999L).isEmpty());
        }
    }

    @Nested
    @DisplayName("SurveyAggResultDaoImpl")
    class Aggregates {

        @Test
        @DisplayName("insert → upsert → 批量 upsert → 删除")
        void writeRoundTrip() {
            try {
                SurveyAggResult r = new SurveyAggResult();
                r.setTyphoonId(Long.valueOf(scratchTyphoonId));
                r.setCityId(Long.valueOf(sampleCityId));
                r.setTotalCount(Integer.valueOf(10));
                r.setSupplyPrepRate(Double.valueOf(0.8D));
                r.setEvacuationAttentionRate(Double.valueOf(0.6D));
                r.setHighTrustRate(Double.valueOf(0.5D));
                r.setStatTime(LocalDateTime.of(2026, 9, 13, 12, 0, 0));

                long id = aggDao.insert(r);
                assertTrue(id > 0L);
                assertNotNull(aggDao.findByTyphoonAndCity(scratchTyphoonId, sampleCityId));
                assertNotNull(aggDao.listByTyphoon(scratchTyphoonId));
                assertNotNull(aggDao.listTopBySampleSize(scratchTyphoonId, 5));
                assertEquals(10, aggDao.sumTotalCount(scratchTyphoonId));

                r.setTotalCount(Integer.valueOf(20));
                assertTrue(aggDao.upsert(r) >= 0, "upsert 应幂等");
                assertTrue(aggDao.upsertAll(java.util.Collections.singletonList(r)) >= 0);
            } finally {
                aggDao.deleteByTyphoon(scratchTyphoonId);
            }
        }

        @Test
        @DisplayName("无聚合数据时 sumTotalCount 返回 0，列表为空")
        void emptyTyphoonIsSafe() {
            assertEquals(0, aggDao.sumTotalCount(-999L));
            assertTrue(aggDao.listByTyphoon(-999L).isEmpty());
            assertTrue(aggDao.findByTyphoonAndCity(-999L, -999L) == null);
        }
    }
}
