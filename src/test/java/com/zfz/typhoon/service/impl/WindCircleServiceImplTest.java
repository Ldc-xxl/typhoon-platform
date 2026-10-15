package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.SystemSettingDao;
import com.zfz.typhoon.dao.WindCircleDao;
import com.zfz.typhoon.model.WindCircle;
import com.zfz.typhoon.model.vo.WindCircleVO;
import com.zfz.typhoon.util.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WindCircleServiceImpl} 单元测试。
 *
 * <p>风圈是"影响范围"的几何基础，本类重点验证：
 * <ol>
 *   <li><b>分级裁剪</b>——最大展示级别来自系统配置，非法配置回退 12；</li>
 *   <li><b>无数据兜底</b>——没有风圈时返回空列表 / 平均半径 0，而不是抛异常；</li>
 *   <li><b>写入校验</b>——缺少台风ID / 观测时间时抛 400。</li>
 * </ol>
 *
 * @author 追风者项目组
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WindCircleServiceImpl · 风圈业务")
class WindCircleServiceImplTest {

    @Mock
    private WindCircleDao windCircleDao;

    @Mock
    private SystemSettingDao settingDao;

    private WindCircleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WindCircleServiceImpl(windCircleDao, settingDao);
    }

    /* ------------------------------------------------------------------ */

    /** 构造一个四象限半径各不相同的风圈，便于断言平均半径。 */
    private WindCircle circle() {
        WindCircle c = new WindCircle();
        c.setTyphoonId(Long.valueOf(1L));
        c.setObsTime(LocalDateTime.of(2026, 9, 1, 8, 0, 0));
        c.setLevel7Ne(Integer.valueOf(100));
        c.setLevel7Se(Integer.valueOf(100));
        c.setLevel7Sw(Integer.valueOf(100));
        c.setLevel7Nw(Integer.valueOf(100));
        c.setLevel10Ne(Integer.valueOf(60));
        c.setLevel10Se(Integer.valueOf(60));
        c.setLevel10Sw(Integer.valueOf(60));
        c.setLevel10Nw(Integer.valueOf(60));
        c.setLevel12Ne(Integer.valueOf(30));
        c.setLevel12Se(Integer.valueOf(30));
        c.setLevel12Sw(Integer.valueOf(30));
        c.setLevel12Nw(Integer.valueOf(30));
        return c;
    }

    private static void assertBusinessError(int code, String part, Runnable action) {
        BusinessException e = assertThrows(BusinessException.class, action::run);
        assertEquals(code, e.getCode(), "业务码不符：" + e.getMessage());
        assertTrue(e.getMessage() != null && e.getMessage().contains(part),
                "提示应包含「" + part + "」，实际：" + e.getMessage());
    }

    /* ==================================================================== */
    /*  listByTyphoon / listAt / latest                                     */
    /* ==================================================================== */

    @Nested
    @DisplayName("listByTyphoon · 当前风圈")
    class ListByTyphoon {

        @Test
        @DisplayName("无数据：返回空列表，且不去读配置")
        void noData() {
            Mockito.when(windCircleDao.findLatest(1L)).thenReturn(null);

            assertTrue(service.listByTyphoon(1L).isEmpty());
            Mockito.verifyNoInteractions(settingDao);
        }

        @Test
        @DisplayName("最大级别 12：返回 7 / 10 / 12 三层")
        void allThreeLevels() {
            Mockito.when(windCircleDao.findLatest(1L)).thenReturn(circle());
            Mockito.when(settingDao.getInt(SystemSettingDao.KEY_WIND_CIRCLE_MAX_LEVEL, 12)).thenReturn(12);

            List<WindCircleVO> list = service.listByTyphoon(1L);

            assertEquals(3, list.size());
            assertEquals(Integer.valueOf(7), list.get(0).getLevel());
            assertEquals(Integer.valueOf(10), list.get(1).getLevel());
            assertEquals(Integer.valueOf(12), list.get(2).getLevel());
            assertEquals(Integer.valueOf(100), list.get(0).getR());
        }

        @Test
        @DisplayName("最大级别 10：只返回 7 / 10 两层")
        void twoLevels() {
            Mockito.when(windCircleDao.findLatest(1L)).thenReturn(circle());
            Mockito.when(settingDao.getInt(SystemSettingDao.KEY_WIND_CIRCLE_MAX_LEVEL, 12)).thenReturn(10);

            List<WindCircleVO> list = service.listByTyphoon(1L);

            assertEquals(2, list.size());
            assertEquals(Integer.valueOf(10), list.get(1).getLevel());
        }

        @Test
        @DisplayName("最大级别 7：只返回 7 级一层")
        void oneLevel() {
            Mockito.when(windCircleDao.findLatest(1L)).thenReturn(circle());
            Mockito.when(settingDao.getInt(SystemSettingDao.KEY_WIND_CIRCLE_MAX_LEVEL, 12)).thenReturn(7);

            List<WindCircleVO> list = service.listByTyphoon(1L);

            assertEquals(1, list.size());
            assertEquals(Integer.valueOf(7), list.get(0).getLevel());
        }

        @Test
        @DisplayName("视图携带四象限半径与配色")
        void voCarriesQuadAndColor() {
            Mockito.when(windCircleDao.findLatest(1L)).thenReturn(circle());
            Mockito.when(settingDao.getInt(SystemSettingDao.KEY_WIND_CIRCLE_MAX_LEVEL, 12)).thenReturn(12);

            WindCircleVO vo = service.listByTyphoon(1L).get(0);

            assertEquals(Integer.valueOf(100), vo.getQuad().get("ne"));
            assertEquals("#FFD166", vo.getColor());
        }
    }

    @Nested
    @DisplayName("listAt / latest / at · 指定时刻与实体")
    class ListAt {

        /** 造一个指定观测时刻的风圈（7 级半径带区分度，便于断言取到的是哪一组）。 */
        private WindCircle circleAt(LocalDateTime t, int r7) {
            WindCircle c = circle();
            c.setObsTime(t);
            c.setLevel7Ne(Integer.valueOf(r7));
            c.setLevel7Se(Integer.valueOf(r7));
            c.setLevel7Sw(Integer.valueOf(r7));
            c.setLevel7Nw(Integer.valueOf(r7));
            return c;
        }

        @Test
        @DisplayName("at(null) 表示「跟随最新」：走 findLatest")
        void atNullMeansLatest() {
            WindCircle c = circle();
            Mockito.when(windCircleDao.findLatest(1L)).thenReturn(c);

            assertEquals(c, service.at(1L, null));
        }

        @Test
        @DisplayName("at 命中该时刻：直接用这一组")
        void atExact() {
            LocalDateTime t = LocalDateTime.of(2026, 9, 1, 8, 0, 0);
            Mockito.when(windCircleDao.findByTyphoonAndTime(1L, t)).thenReturn(circleAt(t, 100));

            assertEquals(Integer.valueOf(100), service.at(1L, t).getLevel7Ne());
        }

        @Test
        @DisplayName("at 未命中该时刻：取「不晚于它的最近一组」，而不是返回 null")
        void atFallsBackToNearestBefore() {
            LocalDateTime t1 = LocalDateTime.of(2026, 9, 1, 8, 0, 0);
            LocalDateTime t2 = LocalDateTime.of(2026, 9, 1, 20, 0, 0);
            LocalDateTime target = LocalDateTime.of(2026, 9, 2, 8, 0, 0);
            Mockito.when(windCircleDao.findByTyphoonAndTime(1L, target)).thenReturn(null);
            Mockito.when(windCircleDao.listByTyphoon(1L))
                    .thenReturn(Arrays.asList(circleAt(t1, 100), circleAt(t2, 150)));

            // 风圈表并非每个观测时次都有数据——多数历史台风整场只存了一组
            // （typhoon_id=1 只有 09-05 17:00 一组，轨迹却有 3 个时次）。
            // 若这里返回 null，「跟随最新」与「锁定最新那个时次」会算出两个不同评分，
            // 而两者本该是同一件事。故必须回退到 09-01 20:00 那组（150）。
            assertEquals(Integer.valueOf(150), service.at(1L, target).getLevel7Ne());
        }

        @Test
        @DisplayName("at 早于所有风圈：如实返回 null（属于数据事实，不造假）")
        void atBeforeAnyReturnsNull() {
            LocalDateTime t = LocalDateTime.of(2026, 8, 1, 8, 0, 0);
            Mockito.when(windCircleDao.findByTyphoonAndTime(1L, t)).thenReturn(null);
            Mockito.when(windCircleDao.listByTyphoon(1L))
                    .thenReturn(Arrays.asList(circleAt(LocalDateTime.of(2026, 9, 1, 8, 0, 0), 100)));

            assertNull(service.at(1L, t));
        }

        @Test
        @DisplayName("listAt 按时刻查询并转视图")
        void listAt() {
            LocalDateTime t = LocalDateTime.of(2026, 9, 1, 8, 0, 0);
            Mockito.when(windCircleDao.findByTyphoonAndTime(1L, t)).thenReturn(circle());
            Mockito.when(settingDao.getInt(SystemSettingDao.KEY_WIND_CIRCLE_MAX_LEVEL, 12)).thenReturn(12);

            assertEquals(3, service.listAt(1L, t).size());
        }

        @Test
        @DisplayName("listAt 查不到时返回空列表")
        void listAtEmpty() {
            LocalDateTime t = LocalDateTime.of(2026, 9, 1, 8, 0, 0);
            Mockito.when(windCircleDao.findByTyphoonAndTime(1L, t)).thenReturn(null);

            assertTrue(service.listAt(1L, t).isEmpty());
        }

        @Test
        @DisplayName("latest 直接返回 DAO 的实体")
        void latest() {
            WindCircle c = circle();
            Mockito.when(windCircleDao.findLatest(1L)).thenReturn(c);

            assertEquals(c, service.latest(1L));
        }
    }

    /* ==================================================================== */
    /*  averageRadius / maxDisplayLevel                                     */
    /* ==================================================================== */

    @Nested
    @DisplayName("averageRadius · 平均半径")
    class AverageRadius {

        @Test
        @DisplayName("无风圈返回 0")
        void noData() {
            Mockito.when(windCircleDao.findLatest(1L)).thenReturn(null);

            assertEquals(0, service.averageRadius(1L, 7));
        }

        @Test
        @DisplayName("有风圈时取四象限平均并四舍五入取整")
        void rounds() {
            WindCircle c = circle();
            // 7 级四象限：100 / 90 / 80 / 70 → 平均 85
            c.setLevel7Se(Integer.valueOf(90));
            c.setLevel7Sw(Integer.valueOf(80));
            c.setLevel7Nw(Integer.valueOf(70));
            Mockito.when(windCircleDao.findLatest(1L)).thenReturn(c);

            assertEquals(85, service.averageRadius(1L, 7));
        }

        @Test
        @DisplayName("某象限为 null 时按 0 参与平均")
        void nullQuadsAsZero() {
            WindCircle c = new WindCircle();
            c.setLevel7Ne(Integer.valueOf(100));
            Mockito.when(windCircleDao.findLatest(1L)).thenReturn(c);

            assertEquals(25, service.averageRadius(1L, 7));
        }
    }

    @Nested
    @DisplayName("maxDisplayLevel · 最大展示级别")
    class MaxDisplayLevel {

        @Test
        @DisplayName("合法配置 7 / 10 / 12 原样返回")
        void legal() {
            Mockito.when(settingDao.getInt(SystemSettingDao.KEY_WIND_CIRCLE_MAX_LEVEL, 12)).thenReturn(7);
            assertEquals(7, service.maxDisplayLevel());
        }

        @Test
        @DisplayName("非法配置回退 12，保证风圈一定画得出来")
        void illegalFallsBack() {
            Mockito.when(settingDao.getInt(SystemSettingDao.KEY_WIND_CIRCLE_MAX_LEVEL, 12)).thenReturn(5);

            assertEquals(12, service.maxDisplayLevel());
        }
    }

    /* ==================================================================== */
    /*  upsert                                                              */
    /* ==================================================================== */

    @Nested
    @DisplayName("upsert · 幂等写入")
    class Upsert {

        @Test
        @DisplayName("缺少 typhoonId 抛 400")
        void missingTyphoonId() {
            WindCircle c = new WindCircle();
            c.setObsTime(LocalDateTime.now());

            assertBusinessError(400, "缺少 typhoonId", () -> service.upsert(c));
        }

        @Test
        @DisplayName("缺少 obsTime 抛 400")
        void missingObsTime() {
            WindCircle c = new WindCircle();
            c.setTyphoonId(Long.valueOf(1L));

            assertBusinessError(400, "缺少观测时间", () -> service.upsert(c));
        }

        @Test
        @DisplayName("合法时按原对象落库")
        void ok() {
            WindCircle c = circle();
            Mockito.when(windCircleDao.upsert(c)).thenReturn(1);

            assertEquals(1, service.upsert(c));
            ArgumentCaptor<WindCircle> captor = ArgumentCaptor.forClass(WindCircle.class);
            Mockito.verify(windCircleDao).upsert(captor.capture());
            assertEquals(Long.valueOf(1L), captor.getValue().getTyphoonId());
        }
    }

    @Test
    @DisplayName("无数据时 latest 返回 null")
    void latestNull() {
        Mockito.when(windCircleDao.findLatest(1L)).thenReturn(null);

        assertNull(service.latest(1L));
    }
}
