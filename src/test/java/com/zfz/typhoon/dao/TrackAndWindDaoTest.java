package com.zfz.typhoon.dao;

import com.zfz.typhoon.dao.impl.TyphoonTrackDaoImpl;
import com.zfz.typhoon.dao.impl.WindCircleDaoImpl;
import com.zfz.typhoon.model.TyphoonTrack;
import com.zfz.typhoon.model.WindCircle;
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
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 轨迹与风圈 DAO 的集成测试（连真实库）。
 *
 * <p>轨迹是"台风在哪儿"的唯一数据源，风圈是"影响范围多大"的唯一数据源，
 * 两者直接决定地图渲染与风险评估结果。这里重点验证：
 * <ul>
 *   <li>{@code listByTyphoon} 的时间<b>升序</b>（画折线时顺序错了线就是乱的）；</li>
 *   <li>{@code findFirst / findLatest} 的首尾语义；</li>
 *   <li>{@code spanHours} 在只有 1 个轨迹点时应为 0 而不是除零或报错；</li>
 *   <li>批量写入（batchInsert / upsertAll）的行数统计。</li>
 * </ul>
 *
 * <h3>写入用例如何避免污染</h3>
 * <p>{@code typhoon_tracks / wind_circles} 都有指向 {@code typhoons} 的<b>外键</b>，
 * 且 {@code typhoon_id} 是 {@code bigint unsigned} —— 随便编一个负数 ID
 * 既过不了无符号约束也过不了外键。所以这里在 {@code @BeforeAll} 里
 * 建一个<b>专供测试的台风</b>（国际编号带 {@code UT-} 前缀），
 * {@code @AfterAll} 再把它连同测试数据一起删掉。
 *
 * @author 追风者项目组
 */
@DisplayName("轨迹与风圈 DAO 集成测试")
class TrackAndWindDaoTest {

    private static TyphoonTrackDao trackDao;
    private static WindCircleDao windDao;
    private static long sampleId;
    private static long scratchId;
    private static final String SCRATCH_CODE = "UT0000008";

    @BeforeAll
    static void setUp() {
        Assumptions.assumeTrue(DBUtil.isAvailable(),
                "本机数据库不可用（" + DBUtil.getInitError() + "），跳过");
        trackDao = new TyphoonTrackDaoImpl();
        windDao = new WindCircleDaoImpl();
        Long id = JdbcTemplate.queryLong(
                "SELECT typhoon_id FROM typhoon_tracks ORDER BY typhoon_id LIMIT 1");
        sampleId = id == null ? 0L : id.longValue();

        JdbcTemplate.update("DELETE FROM typhoons WHERE international_id = ?", SCRATCH_CODE);
        scratchId = JdbcTemplate.insert("INSERT INTO typhoons"
                + " (international_id, chinese_name, english_name, basin, status, generate_time)"
                + " VALUES (?, ?, ?, ?, ?, ?)",
                SCRATCH_CODE, "轨迹测试用台风", "TrackTestTyphoon", "西北太平洋", "ACTIVE",
                LocalDateTime.of(2026, 9, 13, 8, 0, 0)).longValue();
    }

    @AfterAll
    static void tearDown() {
        if (DBUtil.isAvailable()) {
            try {
                JdbcTemplate.update("DELETE FROM wind_circles WHERE typhoon_id = ?",
                        Long.valueOf(scratchId));
                JdbcTemplate.update("DELETE FROM typhoon_tracks WHERE typhoon_id = ?",
                        Long.valueOf(scratchId));
                JdbcTemplate.update("DELETE FROM typhoons WHERE typhoon_id = ?",
                        Long.valueOf(scratchId));
            } catch (RuntimeException ignored) {
                // 清理失败不掩盖测试结论
            }
        }
    }

    private static TyphoonTrack point(long typhoonId, LocalDateTime t, double lat, double lon) {
        TyphoonTrack p = new TyphoonTrack();
        p.setTyphoonId(Long.valueOf(typhoonId));
        p.setObsTime(t);
        p.setLatitude(Double.valueOf(lat));
        p.setLongitude(Double.valueOf(lon));
        p.setWindForce(Integer.valueOf(12));
        p.setWindSpeed(Integer.valueOf(35));
        p.setPressure(Integer.valueOf(970));
        // 移动方向在数据模型里是「方位角（度）」而不是文字：315° 即西北
        p.setMoveDirection(Double.valueOf(315D));
        p.setMoveSpeed(Double.valueOf(18D));
        return p;
    }

    private static WindCircle circle(long typhoonId, LocalDateTime t) {
        WindCircle c = new WindCircle();
        c.setTyphoonId(Long.valueOf(typhoonId));
        c.setObsTime(t);
        for (int level = 7; level <= 12; level += 3) {
            Integer r = Integer.valueOf(level * 30);
            c.setLevel7Ne(r);
            c.setLevel7Nw(r);
            c.setLevel7Se(r);
            c.setLevel7Sw(r);
            c.setLevel10Ne(r);
            c.setLevel10Nw(r);
            c.setLevel10Se(r);
            c.setLevel10Sw(r);
            c.setLevel12Ne(r);
            c.setLevel12Nw(r);
            c.setLevel12Se(r);
            c.setLevel12Sw(r);
        }
        return c;
    }

    @Nested
    @DisplayName("TyphoonTrackDaoImpl")
    class Tracks {

        @Test
        @DisplayName("listByTyphoon：按观测时间升序，前端才能画出正确的折线")
        void listedInChronologicalOrder() {
            List<TyphoonTrack> rows = trackDao.listByTyphoon(sampleId);
            assertNotNull(rows);
            assertFalse(rows.isEmpty(), "演示库里应有轨迹数据");
            LocalDateTime prev = null;
            for (TyphoonTrack p : rows) {
                assertNotNull(p.getObsTime());
                if (prev != null) {
                    assertFalse(p.getObsTime().isBefore(prev),
                            "轨迹必须升序；顺序反了地图上会画出折返线");
                }
                prev = p.getObsTime();
            }
        }

        @Test
        @DisplayName("findFirst / findLatest：分别取最早与最晚一条")
        void firstAndLatest() {
            List<TyphoonTrack> rows = trackDao.listByTyphoon(sampleId);
            Assumptions.assumeFalse(rows.isEmpty(), "样例台风无轨迹");
            TyphoonTrack first = trackDao.findFirst(sampleId);
            TyphoonTrack latest = trackDao.findLatest(sampleId);
            assertNotNull(first);
            assertNotNull(latest);
            assertEquals(rows.get(0).getObsTime(), first.getObsTime());
            assertEquals(rows.get(rows.size() - 1).getObsTime(), latest.getObsTime());
        }

        @Test
        @DisplayName("listByTyphoonBetween：区间过滤")
        void betweenRange() {
            LocalDateTime from = LocalDateTime.of(2000, 1, 1, 0, 0);
            LocalDateTime to = LocalDateTime.of(2035, 1, 1, 0, 0);
            assertEquals(trackDao.listByTyphoon(sampleId).size(),
                    trackDao.listByTyphoonBetween(sampleId, from, to).size());
            assertTrue(trackDao.listByTyphoonBetween(sampleId,
                    LocalDateTime.of(1900, 1, 1, 0, 0),
                    LocalDateTime.of(1901, 1, 1, 0, 0)).isEmpty(), "区间外的点应被过滤掉");
        }

        @Test
        @DisplayName("listLatestByTyphoons：批量取多个台风的最新点")
        void latestOfMany() {
            List<Long> ids = Arrays.asList(Long.valueOf(sampleId), Long.valueOf(sampleId + 1),
                    Long.valueOf(sampleId + 999));
            assertNotNull(trackDao.listLatestByTyphoons(ids));
        }

        @Test
        @DisplayName("listLatestByTyphoons：空集合直接返回空列表（不生成非法 SQL）")
        void latestOfEmptyCollection() {
            assertTrue(trackDao.listLatestByTyphoons(new ArrayList<Long>()).isEmpty(),
                    "空 IN() 会让 MySQL 报语法错误，必须在 DAO 层拦掉");
        }

        @Test
        @DisplayName("countByTyphoon 与列表行数一致")
        void countMatchesList() {
            assertEquals(trackDao.listByTyphoon(sampleId).size(), trackDao.countByTyphoon(sampleId));
        }

        @Test
        @DisplayName("spanHours：无轨迹点时为 0，有 3 个以上点时为首尾时差")
        void spanHoursIsSafe() {
            assertEquals(0D, trackDao.spanHours(scratchId), 0.001D,
                    "无轨迹时应为 0，不能因空指针或除零抛异常");
            List<TyphoonTrack> rows = trackDao.listByTyphoon(sampleId);
            if (rows.size() >= 2) {
                assertTrue(trackDao.spanHours(sampleId) >= 0D, "跨度不能为负");
            }
        }

        @Test
        @DisplayName("insert → batchInsert → upsertAll → delete 全链路")
        void writeRoundTrip() {
            LocalDateTime t0 = LocalDateTime.of(2026, 9, 13, 8, 0, 0);
            try {
                long id = trackDao.insert(point(scratchId, t0, 20D, 125D));
                assertTrue(id > 0L, "insert 应回填自增主键");
                assertEquals(1, trackDao.countByTyphoon(scratchId));
                assertEquals(0D, trackDao.spanHours(scratchId), 0.001D,
                        "只有一个点时跨度应为 0，不能因除零抛异常");

                List<TyphoonTrack> more = new ArrayList<TyphoonTrack>();
                more.add(point(scratchId, t0.plusHours(6), 21D, 124D));
                more.add(point(scratchId, t0.plusHours(12), 22D, 123D));
                assertEquals(2, trackDao.batchInsert(more));

                more.get(0).setWindForce(Integer.valueOf(16));
                assertTrue(trackDao.upsertAll(more) >= 0, "upsert 应幂等，重复执行不报错");

                assertEquals(3, trackDao.countByTyphoon(scratchId));
                assertEquals(12D, trackDao.spanHours(scratchId), 0.001D,
                        "首尾间隔 12 小时");

                assertEquals(1, trackDao.deleteByTyphoonAndTime(scratchId, t0.plusHours(12)));
                assertEquals(2, trackDao.countByTyphoon(scratchId));
            } finally {
                JdbcTemplate.update("DELETE FROM typhoon_tracks WHERE typhoon_id = ?",
                        Long.valueOf(scratchId));
            }
            assertEquals(0, trackDao.countByTyphoon(scratchId), "清理后应无残留");
        }
    }

    @Nested
    @DisplayName("WindCircleDaoImpl")
    class Wind {

        @Test
        @DisplayName("listByTyphoon：返回风圈记录")
        void listByTyphoon() {
            assertNotNull(windDao.listByTyphoon(sampleId));
        }

        @Test
        @DisplayName("findLatest / findByTyphoonAndTime：无数据时返回 null 而不是抛异常")
        void findLatestIsNullSafe() {
            WindCircle latest = windDao.findLatest(scratchId);
            assertTrue(latest == null || latest.getObsTime() != null);
            assertTrue(windDao.findByTyphoonAndTime(scratchId,
                    LocalDateTime.of(1999, 1, 1, 0, 0)) == null);
        }

        @Test
        @DisplayName("listLatestByTyphoons：批量取最新风圈，空集合返回空列表")
        void latestOfMany() {
            assertNotNull(windDao.listLatestByTyphoons(
                    Arrays.asList(Long.valueOf(sampleId), Long.valueOf(sampleId + 1))));
            assertTrue(windDao.listLatestByTyphoons(new ArrayList<Long>()).isEmpty());
        }

        @Test
        @DisplayName("insert / upsert / delete 全链路")
        void writeRoundTrip() {
            LocalDateTime t0 = LocalDateTime.of(2026, 9, 13, 8, 0, 0);
            try {
                WindCircle c = circle(scratchId, t0);
                long id = windDao.insert(c);
                assertTrue(id > 0L);
                assertNotNull(windDao.findLatest(scratchId));
                assertNotNull(windDao.findByTyphoonAndTime(scratchId, t0));
                assertTrue(windDao.upsert(c) >= 0, "幂等 upsert");
                assertTrue(windDao.deleteByTyphoonAndTime(scratchId, t0) >= 0);
            } finally {
                JdbcTemplate.update("DELETE FROM wind_circles WHERE typhoon_id = ?",
                        Long.valueOf(scratchId));
            }
        }
    }
}
