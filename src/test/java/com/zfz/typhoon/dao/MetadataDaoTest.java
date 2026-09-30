package com.zfz.typhoon.dao;

import com.zfz.typhoon.dao.impl.CityImpactRecordDaoImpl;
import com.zfz.typhoon.dao.impl.DataSourceDaoImpl;
import com.zfz.typhoon.dao.impl.DisasterReportDaoImpl;
import com.zfz.typhoon.dao.impl.FeedbackReportDaoImpl;
import com.zfz.typhoon.dao.impl.SyncLogDaoImpl;
import com.zfz.typhoon.dao.impl.TyphoonAlbumDaoImpl;
import com.zfz.typhoon.model.CityImpactRecord;
import com.zfz.typhoon.model.DataSource;
import com.zfz.typhoon.model.DisasterReport;
import com.zfz.typhoon.model.FeedbackReport;
import com.zfz.typhoon.model.SyncLog;
import com.zfz.typhoon.model.TyphoonAlbum;
import com.zfz.typhoon.util.DBUtil;
import com.zfz.typhoon.util.JdbcTemplate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据源 / 同步日志 / 灾情报告 / 城市影响 / 影像 / 反馈 DAO 的集成测试。
 *
 * <p>这几张表支撑后台的"数据源监控"与前台的"灾情档案"，
 * 共同点是<b>数据可能整张表为空</b>（比如一次同步都没跑过），
 * 所以每个用例都先验证"空表时不报错"——
 * 后台监控页最怕的就是因为除零或空指针直接白屏。
 *
 * @author 追风者项目组
 */
@DisplayName("元数据与运维 DAO 集成测试")
class MetadataDaoTest {

    private static DataSourceDao sourceDao;
    private static SyncLogDao logDao;
    private static DisasterReportDao disasterDao;
    private static CityImpactRecordDao impactDao;
    private static TyphoonAlbumDao albumDao;
    private static FeedbackReportDao feedbackDao;

    private static long sampleTyphoonId;
    private static long sampleCityId;
    private static long sampleUserId;
    private static long scratchTyphoonId;
    private static final String SCRATCH_CODE = "UT0000005";

    @BeforeAll
    static void setUp() {
        Assumptions.assumeTrue(DBUtil.isAvailable(),
                "本机数据库不可用（" + DBUtil.getInitError() + "），跳过");
        sourceDao = new DataSourceDaoImpl();
        logDao = new SyncLogDaoImpl();
        disasterDao = new DisasterReportDaoImpl();
        impactDao = new CityImpactRecordDaoImpl();
        albumDao = new TyphoonAlbumDaoImpl();
        feedbackDao = new FeedbackReportDaoImpl();

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
                SCRATCH_CODE, "元数据测试用台风", "MetaTestTyphoon", "西北太平洋", "ACTIVE",
                LocalDateTime.of(2026, 9, 13, 8, 0, 0)).longValue();
    }

    @AfterAll
    static void tearDown() {
        if (!DBUtil.isAvailable()) {
            return;
        }
        try {
            impactDao.deleteByTyphoon(scratchTyphoonId);
            albumDao.deleteByTyphoon(scratchTyphoonId);
            JdbcTemplate.update("DELETE FROM typhoons WHERE typhoon_id = ?",
                    Long.valueOf(scratchTyphoonId));
        } catch (RuntimeException ignored) {
            // 清理失败不掩盖测试结论
        }
    }

    @Nested
    @DisplayName("DataSourceDaoImpl")
    class Sources {

        @Test
        @DisplayName("listAll / listEnabled / countEnabled / findByCode")
        void reads() {
            List<DataSource> all = sourceDao.listAll();
            assertNotNull(all);
            assertNotNull(sourceDao.listEnabled());
            assertTrue(sourceDao.countEnabled() <= all.size());
            assertTrue(sourceDao.findById(-999L) == null);
            assertTrue(sourceDao.findByCode("no-such-code") == null);
            for (DataSource d : all) {
                assertNotNull(d.getSourceCode(), "数据源编码是同步任务的定位键");
            }
        }

        @Test
        @DisplayName("写入：insert → 启停用 → 更新同步时间 → update → delete")
        void writeRoundTrip() {
            DataSource d = new DataSource();
            d.setSourceCode("UT_SOURCE");
            d.setSourceName("单元测试数据源");
            d.setApiUrl("http://127.0.0.1/fake");
            d.setUpdateFrequency(Integer.valueOf(60));
            d.setEnabled(Boolean.TRUE);
            d.setDescription("由单元测试创建");

            JdbcTemplate.update("DELETE FROM data_sources WHERE source_code = ?", "UT_SOURCE");
            long id = sourceDao.insert(d);
            try {
                assertNotNull(sourceDao.findById(id));
                assertNotNull(sourceDao.findByCode("UT_SOURCE"));
                assertEquals(1, sourceDao.setEnabled(id, false));
                assertEquals(1, sourceDao.updateLastSyncTime(id, LocalDateTime.of(2026, 9, 13, 12, 0, 0)));
                DataSource saved = sourceDao.findById(id);
                saved.setDescription("改过描述");
                assertEquals(1, sourceDao.update(saved));
                assertEquals("改过描述", sourceDao.findById(id).getDescription());
            } finally {
                sourceDao.deleteById(id);
            }
            assertTrue(sourceDao.findById(id) == null);
        }
    }

    @Nested
    @DisplayName("SyncLogDaoImpl")
    class Logs {

        @Test
        @DisplayName("空数据源的统计口径：成功率 0 而不是 NaN，计数 0")
        void metricsOnEmptySource() {
            assertEquals(0D, logDao.successRate(-999L), 0.0001D);
            assertEquals(0D, logDao.avgDurationSeconds(-999L), 0.0001D);
            assertEquals(0, logDao.countBySource(-999L));
            assertEquals(0, logDao.sumRecordCountToday(-999L));
            assertTrue(logDao.findLastSyncTime(-999L) == null);
            assertTrue(logDao.listBySource(-999L, 10).isEmpty());
        }

        @Test
        @DisplayName("listRecent / listFailed / listByStatus / countByStatus / countAll")
        void reads() {
            assertNotNull(logDao.listRecent(10));
            assertNotNull(logDao.listFailed(10));
            assertNotNull(logDao.listByStatus("SUCCESS", 10));
            assertTrue(logDao.countByStatus("SUCCESS") >= 0);
            assertTrue(logDao.countAll() >= 0);
        }

        @Test
        @DisplayName("写入：insert → 成功率统计 → 按时间清理")
        void writeRoundTrip() {
            Long sourceId = JdbcTemplate.queryLong("SELECT MIN(source_id) FROM data_sources");
            Assumptions.assumeTrue(sourceId != null, "库里没有数据源，跳过");
            long sid = sourceId.longValue();
            int before = logDao.countBySource(sid);

            SyncLog ok = new SyncLog();
            ok.setSourceId(Long.valueOf(sid));
            ok.setSyncTime(LocalDateTime.of(2026, 9, 13, 10, 0, 0));
            ok.setRecordCount(Integer.valueOf(100));
            ok.setSyncStatus("SUCCESS");
            ok.setDurationSeconds(Integer.valueOf(3));
            long okId = logDao.insert(ok);

            SyncLog bad = new SyncLog();
            bad.setSourceId(Long.valueOf(sid));
            bad.setSyncTime(LocalDateTime.of(2026, 9, 13, 11, 0, 0));
            bad.setRecordCount(Integer.valueOf(0));
            bad.setSyncStatus("FAILED");
            bad.setErrorMessage("测试用失败");
            bad.setDurationSeconds(Integer.valueOf(5));
            long badId = logDao.insert(bad);
            try {
                assertEquals(before + 2, logDao.countBySource(sid), "刚插入的两条应能统计到");
                assertTrue(logDao.countBySourceAndStatus(sid, "SUCCESS") >= 1);
                double rate = logDao.successRate(sid);
                assertTrue(rate >= 0D && rate <= 100D,
                        "successRate 的口径是百分数（0~100），实际=" + rate);
                assertTrue(logDao.avgDurationSeconds(sid) >= 0D);
                assertNotNull(logDao.findLastSyncTime(sid));
                assertFalse(logDao.listBySource(sid, 10).isEmpty());
                assertTrue(logDao.deleteBefore(LocalDateTime.of(2026, 9, 13, 10, 30, 0)) >= 1);
            } finally {
                JdbcTemplate.update("DELETE FROM sync_logs WHERE log_id IN (?, ?)",
                        Long.valueOf(okId), Long.valueOf(badId));
            }
        }
    }

    @Nested
    @DisplayName("DisasterReportDaoImpl")
    class Disasters {

        @Test
        @DisplayName("无灾情记录时返回空/null 而不是报错")
        void readsAreNullSafe() {
            assertTrue(disasterDao.listByTyphoon(-999L).isEmpty());
            assertTrue(disasterDao.findLatestByTyphoon(-999L) == null);
            assertTrue(disasterDao.findById(-999L) == null);
            assertNotNull(disasterDao.listTyphoonIdsWithReport());
        }

        @Test
        @DisplayName("insert → 取最新 → update → delete（用测试台风）")
        void writeRoundTrip() {
            try {
                DisasterReport d = new DisasterReport();
                d.setTyphoonId(Long.valueOf(scratchTyphoonId));
                d.setMainCityId(Long.valueOf(sampleCityId));
                d.setAffectedProvinces("广东");
                d.setDirectEconomicLoss(Double.valueOf(1234.5D));
                d.setCasualties(Integer.valueOf(0));
                d.setInjured(Integer.valueOf(2));
                d.setAffectedPopulation(Integer.valueOf(5000));
                d.setDisasterSummary("单元测试灾情");
                d.setReportSource("单元测试");
                d.setReportTime(LocalDateTime.of(2026, 9, 14, 9, 0, 0));

                long id = disasterDao.insert(d);
                assertTrue(id > 0L);
                assertNotNull(disasterDao.findById(id));
                assertNotNull(disasterDao.findLatestByTyphoon(scratchTyphoonId));
                assertEquals(1, disasterDao.listByTyphoon(scratchTyphoonId).size());

                DisasterReport saved = disasterDao.findById(id);
                saved.setDisasterSummary("改过的灾情");
                assertEquals(1, disasterDao.update(saved));

                assertEquals(1, disasterDao.deleteByTyphoon(scratchTyphoonId));
            } finally {
                disasterDao.deleteByTyphoon(scratchTyphoonId);
            }
        }
    }

    @Nested
    @DisplayName("CityImpactRecordDaoImpl")
    class Impacts {

        @Test
        @DisplayName("无影响记录时安全返回")
        void readsAreNullSafe() {
            assertTrue(impactDao.listByTyphoon(-999L).isEmpty());
            assertTrue(impactDao.listByTyphoonAndCity(-999L, -998L).isEmpty());
            assertTrue(impactDao.listByWindCircleLevel(-999L, 7).isEmpty());
            assertEquals(0, impactDao.countByTyphoon(-999L));
            assertTrue(impactDao.findLatestGeneratedAt(-999L) == null);
        }

        @Test
        @DisplayName("批量写入 + 幂等 upsert + 按风圈等级筛选")
        void writeRoundTrip() {
            try {
                List<CityImpactRecord> rows = new ArrayList<CityImpactRecord>();
                for (int level : new int[]{7, 10, 12}) {
                    CityImpactRecord r = new CityImpactRecord();
                    r.setTyphoonId(Long.valueOf(scratchTyphoonId));
                    r.setCityId(Long.valueOf(sampleCityId));
                    r.setWindCircleLevel(Integer.valueOf(level));
                    r.setEstimatedWindForce(Integer.valueOf(8 + level));
                    r.setImpactStartTime(LocalDateTime.of(2026, 9, 14, 8, 0, 0));
                    r.setImpactEndTime(LocalDateTime.of(2026, 9, 14, 20, 0, 0));
                    r.setDistanceKm(Double.valueOf(50D));
                    r.setGeneratedAt(LocalDateTime.of(2026, 9, 13, 12, 0, 0));
                    rows.add(r);
                }
                assertEquals(3, impactDao.batchInsert(rows));
                assertEquals(3, impactDao.countByTyphoon(scratchTyphoonId));
                assertEquals(1, impactDao.listByWindCircleLevel(scratchTyphoonId, 10).size());
                assertEquals(3, impactDao.listByTyphoonAndCity(scratchTyphoonId, sampleCityId).size());
                assertNotNull(impactDao.findLatestGeneratedAt(scratchTyphoonId));

                assertTrue(impactDao.upsertAll(rows) >= 0, "重算后应覆盖而不是翻倍");
                assertEquals(3, impactDao.countByTyphoon(scratchTyphoonId));
            } finally {
                impactDao.deleteByTyphoon(scratchTyphoonId);
            }
        }
    }

    @Nested
    @DisplayName("TyphoonAlbumDaoImpl")
    class Album {

        @Test
        @DisplayName("无影像时安全返回")
        void readsAreNullSafe() {
            assertTrue(albumDao.listByTyphoon(-999L).isEmpty());
            assertTrue(albumDao.listByTyphoonAndType(-999L, "SATELLITE").isEmpty());
            assertTrue(albumDao.findLatest(-999L, "SATELLITE") == null);
        }

        @Test
        @DisplayName("insert → 按类型查 → 批量插入 → 删除")
        void writeRoundTrip() {
            try {
                TyphoonAlbum a = new TyphoonAlbum();
                a.setTyphoonId(Long.valueOf(scratchTyphoonId));
                a.setImageType("SATELLITE");
                a.setImageUrl("/img/ut-1.png");
                a.setShotTime(LocalDateTime.of(2026, 9, 13, 9, 0, 0));
                a.setDescription("单元测试影像");
                long id = albumDao.insert(a);
                assertTrue(id > 0L);

                assertEquals(1, albumDao.listByTyphoon(scratchTyphoonId).size());
                assertEquals(1, albumDao.listByTyphoonAndType(scratchTyphoonId, "SATELLITE").size());
                assertNotNull(albumDao.findLatest(scratchTyphoonId, "SATELLITE"));
                assertTrue(albumDao.listByTyphoonAndType(scratchTyphoonId, "PHOTO").isEmpty());

                TyphoonAlbum b = new TyphoonAlbum();
                b.setTyphoonId(Long.valueOf(scratchTyphoonId));
                b.setImageType("PHOTO");
                b.setImageUrl("/img/ut-2.png");
                b.setShotTime(LocalDateTime.of(2026, 9, 13, 10, 0, 0));
                assertEquals(1, albumDao.batchInsert(java.util.Collections.singletonList(b)));
                assertEquals(2, albumDao.listByTyphoon(scratchTyphoonId).size());
            } finally {
                albumDao.deleteByTyphoon(scratchTyphoonId);
            }
        }
    }

    @Nested
    @DisplayName("FeedbackReportDaoImpl")
    class Feedback {

        @Test
        @DisplayName("列表与统计：空结果安全返回")
        void reads() {
            assertNotNull(feedbackDao.list(null, null, 10));
            assertNotNull(feedbackDao.list("PENDING", "BUG", 10));
            assertNotNull(feedbackDao.listByUser(sampleUserId));
            assertTrue(feedbackDao.findById(-999L) == null);
            assertTrue(feedbackDao.countAll() >= 0);
            assertTrue(feedbackDao.countByStatus("PENDING") >= 0);
            assertTrue(feedbackDao.countByType("BUG") >= 0);
        }

        @Test
        @DisplayName("insert → 处理 → 重开 → 删除")
        void writeRoundTrip() {
            FeedbackReport f = new FeedbackReport();
            f.setUserId(Long.valueOf(sampleUserId));
            f.setFeedbackType("BUG");
            f.setContent("单元测试反馈");
            f.setContactInfo("ut@example.com");
            f.setStatus("PENDING");
            long id = feedbackDao.insert(f);
            try {
                assertNotNull(feedbackDao.findById(id));
                assertEquals(1, feedbackDao.handle(id, sampleUserId,
                        LocalDateTime.of(2026, 9, 13, 15, 0, 0)));
                assertEquals(1, feedbackDao.reopen(id));
                assertFalse(feedbackDao.listByUser(sampleUserId).isEmpty());
            } finally {
                assertEquals(1, feedbackDao.deleteById(id));
            }
            assertTrue(feedbackDao.findById(id) == null);
        }
    }
}
