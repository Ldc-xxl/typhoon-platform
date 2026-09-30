package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.CityDao;
import com.zfz.typhoon.dao.RiskAssessmentDao;
import com.zfz.typhoon.dao.SystemSettingDao;
import com.zfz.typhoon.model.City;
import com.zfz.typhoon.model.RiskAssessment;
import com.zfz.typhoon.model.Typhoon;
import com.zfz.typhoon.model.TyphoonTrack;
import com.zfz.typhoon.model.WindCircle;
import com.zfz.typhoon.model.vo.RiskAssessmentVO;
import com.zfz.typhoon.service.RiskAssessmentService;
import com.zfz.typhoon.service.CityService;
import com.zfz.typhoon.service.TyphoonService;
import com.zfz.typhoon.service.WindCircleService;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RiskAssessmentServiceImpl} 单元测试。
 *
 * <p>评分模型有三个分量（风 60 / 距离 25 / 脆弱性 15，合计 0—100），
 * 本类按分量逐一构造场景验证：
 * <ul>
 *   <li><b>风圈优先</b>——城市落入某等级风圈时直接取该等级（12 级圈还要与中心风力取大）；</li>
 *   <li><b>圈外衰减</b>——每远离 60 km 递减 1 级，下限 1 级；</li>
 *   <li><b>脆弱性兜底</b>——城市基准风险缺失时按中位 0.5；</li>
 *   <li><b>除零防护</b>——评估半径配置为 0 时必须回退默认 800 km；
 *       台风停滞（移速 &le; 1 km/h）时不给 ETA，避免除以近零移速。</li>
 * </ul>
 *
 * <p>所有分档断言都以固定几何量算得，不依赖当前时间；含 {@code now()} 的字段只做非空断言。
 *
 * @author 追风者项目组
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RiskAssessmentServiceImpl · 城市风险预评估")
class RiskAssessmentServiceImplTest {

    private static final long TYPHOON_ID = 1L;

    /** 评估中心（台风实测位置） */
    private static final double CENTER_LAT = 20.0D;
    private static final double CENTER_LON = 120.0D;

    @Mock
    private RiskAssessmentDao riskDao;

    @Mock
    private CityDao cityDao;

    @Mock
    private SystemSettingDao settingDao;

    @Mock
    private TyphoonService typhoonService;

    @Mock
    private CityService cityService;

    @Mock
    private WindCircleService windCircleService;

    private RiskAssessmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RiskAssessmentServiceImpl(riskDao, cityDao, settingDao,
                typhoonService, cityService, windCircleService);
    }

    /* ------------------------------------------------------------------ */
    /*  数据构造                                                            */
    /* ------------------------------------------------------------------ */

    private Typhoon typhoon() {
        Typhoon t = new Typhoon();
        t.setTyphoonId(Long.valueOf(TYPHOON_ID));
        t.setChineseName("测试台风");
        return t;
    }

    private City city(long id, String name, Integer baseRisk, Double lat, Double lon) {
        City c = new City();
        c.setCityId(Long.valueOf(id));
        c.setCityName(name);
        c.setProvince("测试省");
        c.setLatitude(lat);
        c.setLongitude(lon);
        c.setBaseRiskLevel(baseRisk);
        return c;
    }

    private TyphoonTrack track(Double lat, Double lon, Integer force, Double speed) {
        TyphoonTrack t = new TyphoonTrack();
        t.setTyphoonId(Long.valueOf(TYPHOON_ID));
        t.setObsTime(LocalDateTime.now().minusHours(2));
        t.setLatitude(lat);
        t.setLongitude(lon);
        t.setWindForce(force);
        t.setMoveSpeed(speed);
        return t;
    }

    /** 仅设置东北象限半径的风圈（城市都在中心东北方向，故取 ne 象限）。 */
    private WindCircle circle(Integer level12, Integer level10, Integer level7) {
        WindCircle c = new WindCircle();
        c.setLevel12Ne(level12);
        c.setLevel10Ne(level10);
        c.setLevel7Ne(level7);
        return c;
    }

    private Map<Long, City> asMap(List<City> cities) {
        Map<Long, City> map = new LinkedHashMap<Long, City>();
        for (City c : cities) {
            map.put(c.getCityId(), c);
        }
        return map;
    }

    /** 组装一次 assess 所需的全部桩（台风、轨迹、风圈、配置、城市表）。 */
    private List<RiskAssessmentVO> assessWith(List<City> cities, WindCircle circle,
                                              Integer force, Double speed, double rangeKm) {
        Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
        Mockito.when(typhoonService.latestTrack(TYPHOON_ID))
                .thenReturn(track(Double.valueOf(CENTER_LAT), Double.valueOf(CENTER_LON), force, speed));
        Mockito.when(windCircleService.latest(TYPHOON_ID)).thenReturn(circle);
        Mockito.when(settingDao.getDouble(Mockito.anyString(), Mockito.anyDouble()))
                .thenReturn(Double.valueOf(rangeKm));
        Mockito.when(cityService.asMap()).thenReturn(asMap(cities));
        return service.assess(TYPHOON_ID);
    }

    /* ==================================================================== */
    /*  listLatest                                                          */
    /* ==================================================================== */

    @Nested
    @DisplayName("listLatest · 已落库结果")
    class ListLatest {

        @Test
        @DisplayName("按评分降序返回，并带出城市名；城市查不到时如实留空")
        void sortedDescWithCityJoin() {
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            Mockito.when(riskDao.listLatestByTyphoon(TYPHOON_ID)).thenReturn(Arrays.asList(
                    assessment(1L, "HIGH", 80.5D),
                    assessment(2L, "LOW", 30.0D),
                    assessment(null, "MEDIUM", 50.0D)));
            Mockito.when(cityDao.findById(1L)).thenReturn(city(1L, "厦门", 4, 24.0, 118.0));
            Mockito.when(cityDao.findById(2L)).thenReturn(null);

            List<RiskAssessmentVO> list = service.listLatest(TYPHOON_ID);

            assertEquals(3, list.size());
            assertEquals(80.5D, list.get(0).getRiskScore().doubleValue(), 1e-9);
            assertEquals("厦门", list.get(0).getCityName());
            assertEquals("高", list.get(0).getRiskLevel());
            assertEquals("HIGH", list.get(0).getLevel());
            assertEquals(30.0D, list.get(2).getRiskScore().doubleValue(), 1e-9);
            assertNull(list.get(1).getCityName());
        }

        @Test
        @DisplayName("库中无数据时返回空列表")
        void empty() {
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            Mockito.when(riskDao.listLatestByTyphoon(TYPHOON_ID))
                    .thenReturn(new ArrayList<RiskAssessment>());

            assertTrue(service.listLatest(TYPHOON_ID).isEmpty());
        }

        private RiskAssessment assessment(Long cityId, String level, double score) {
            RiskAssessment ra = new RiskAssessment();
            ra.setTyphoonId(Long.valueOf(TYPHOON_ID));
            ra.setCityId(cityId);
            ra.setRiskLevel(level);
            ra.setRiskScore(Double.valueOf(score));
            ra.setSuggestion("建议关注");
            return ra;
        }
    }

    /* ==================================================================== */
    /*  assess · 前提条件                                                    */
    /* ==================================================================== */

    @Nested
    @DisplayName("assess · 前提校验")
    class AssessPreconditions {

        @Test
        @DisplayName("无实测轨迹 → 400")
        void noTrack() {
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            Mockito.when(typhoonService.latestTrack(TYPHOON_ID)).thenReturn(null);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> service.assess(TYPHOON_ID));
            assertEquals(Result.CODE_BAD_REQUEST, e.getCode());
            assertTrue(e.getMessage().contains("暂无实测轨迹"));
        }

        @Test
        @DisplayName("轨迹缺经纬度 → 400")
        void trackWithoutCoordinates() {
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            Mockito.when(typhoonService.latestTrack(TYPHOON_ID))
                    .thenReturn(track(null, Double.valueOf(CENTER_LON), Integer.valueOf(10), Double.valueOf(20D)));

            assertThrows(BusinessException.class, () -> service.assess(TYPHOON_ID));
        }

        @Test
        @DisplayName("缺经纬度的城市与超出半径的城市都被跳过")
        void skipsInvalidAndFarCities() {
            List<City> cities = Arrays.asList(
                    city(1L, "缺坐标", 4, null, null),
                    city(2L, "太远", 4, Double.valueOf(40.0), Double.valueOf(150.0)));
            List<RiskAssessmentVO> list = assessWith(cities, circle(50, 200, 400),
                    Integer.valueOf(15), Double.valueOf(20D), 800D);

            assertTrue(list.isEmpty());
        }
    }

    /* ==================================================================== */
    /*  assess · 评分分级                                                    */
    /* ==================================================================== */

    @Nested
    @DisplayName("assess · 评分与分级")
    class Scoring {

        /** 四个依次远离中心的城市，恰好落在 12 / 10 / 7 级风圈与圈外。 */
        private List<City> ladderCities() {
            return Arrays.asList(
                    city(1L, "厦门", Integer.valueOf(4), Double.valueOf(20.05), Double.valueOf(120.0)),
                    city(2L, "泉州", Integer.valueOf(3), Double.valueOf(21.0), Double.valueOf(120.0)),
                    city(3L, "福州", Integer.valueOf(2), Double.valueOf(22.0), Double.valueOf(120.0)),
                    city(5L, "杭州", Integer.valueOf(1), Double.valueOf(26.0), Double.valueOf(120.0)));
        }

        @Test
        @DisplayName("风圈由内向外分档：EXTREME / HIGH / MEDIUM / LOW 且按分值降序")
        void levelsByCircle() {
            List<RiskAssessmentVO> list = assessWith(ladderCities(), circle(50, 200, 400),
                    Integer.valueOf(15), Double.valueOf(20D), 800D);

            assertEquals(4, list.size());
            assertEquals("EXTREME", list.get(0).getLevel());
            assertEquals("极高", list.get(0).getRiskLevel());
            assertEquals("15 级", list.get(0).getWindLevel());
            assertTrue(list.get(0).getInWindCircle().booleanValue());
            assertTrue(list.get(0).getAdvice().contains("风险极高"));

            assertEquals("HIGH", list.get(1).getLevel());
            assertEquals("10 级", list.get(1).getWindLevel());
            assertTrue(list.get(1).getAdvice().contains("加固门窗"));

            assertEquals("MEDIUM", list.get(2).getLevel());
            assertEquals("7 级", list.get(2).getWindLevel());
            assertTrue(list.get(2).getInWindCircle().booleanValue());
            assertTrue(list.get(2).getAdvice().contains("避免前往海边"));

            assertEquals("LOW", list.get(3).getLevel());
            assertEquals("低", list.get(3).getRiskLevel());
            assertFalse(list.get(3).getInWindCircle().booleanValue());
            assertTrue(list.get(3).getAdvice().contains("受外围环流影响有限"));

            // 降序
            for (int i = 1; i < list.size(); i++) {
                assertTrue(list.get(i - 1).getRiskScore().doubleValue()
                        >= list.get(i).getRiskScore().doubleValue());
            }
        }

        @Test
        @DisplayName("12 级风圈内中心风力更高时以中心风力为准")
        void twelveCircleTakesMaxCenterForce() {
            List<City> cities = Arrays.asList(
                    city(1L, "厦门", Integer.valueOf(4), Double.valueOf(20.02), Double.valueOf(120.0)));

            List<RiskAssessmentVO> list = assessWith(cities, circle(50, 200, 400),
                    Integer.valueOf(17), Double.valueOf(20D), 800D);

            assertEquals("17 级", list.get(0).getWindLevel());
        }

        @Test
        @DisplayName("风圈外按每 60 km 衰减 1 级；中心风力为 0 时按经验值 7 级起算")
        void decayOutsideCircles() {
            List<City> cities = Arrays.asList(
                    city(1L, "近城", Integer.valueOf(4), Double.valueOf(21.0), Double.valueOf(120.0)));

            List<RiskAssessmentVO> list = assessWith(cities, null,
                    Integer.valueOf(0), Double.valueOf(10D), 800D);

            // 距中心约 111 km；7 - 111/60 ≈ 5.15 → 5 级
            assertEquals("5 级", list.get(0).getWindLevel());
            assertFalse(list.get(0).getInWindCircle().booleanValue());
        }

        @Test
        @DisplayName("远离中心时衰减到下限 1 级")
        void decayFloorAtOne() {
            List<City> cities = Arrays.asList(
                    city(1L, "远城", Integer.valueOf(1), Double.valueOf(26.0), Double.valueOf(120.0)));

            List<RiskAssessmentVO> list = assessWith(cities, null,
                    Integer.valueOf(0), Double.valueOf(10D), 800D);

            assertEquals("1 级", list.get(0).getWindLevel());
        }

        @Test
        @DisplayName("圈外的 MEDIUM 建议措辞与圈内不同（走另一分支）")
        void mediumOutsideCircleAdvice() {
            List<City> cities = Arrays.asList(
                    city(1L, "边城", Integer.valueOf(4), Double.valueOf(20.54), Double.valueOf(120.0)));

            List<RiskAssessmentVO> list = assessWith(cities, null,
                    Integer.valueOf(5), Double.valueOf(10D), 800D);

            assertEquals("MEDIUM", list.get(0).getLevel());
            assertFalse(list.get(0).getInWindCircle().booleanValue());
            assertTrue(list.get(0).getAdvice().contains("减少不必要的外出"));
        }

        @Test
        @DisplayName("城市基准风险缺失按中位 0.5，与显式 2 等价")
        void nullBaseRiskEqualsLevelTwo() {
            List<City> cities = Arrays.asList(
                    city(1L, "甲", null, Double.valueOf(20.5), Double.valueOf(120.0)),
                    city(2L, "乙", Integer.valueOf(2), Double.valueOf(20.5), Double.valueOf(120.0)));

            List<RiskAssessmentVO> list = assessWith(cities, null,
                    Integer.valueOf(15), Double.valueOf(10D), 800D);

            assertEquals(2, list.size());
            assertEquals(list.get(0).getRiskScore().doubleValue(),
                    list.get(1).getRiskScore().doubleValue(), 1e-9);
        }
    }

    /* ==================================================================== */
    /*  assess · ETA 与除零防护                                              */
    /* ==================================================================== */

    @Nested
    @DisplayName("assess · ETA 与除零防护")
    class EtaAndDivision {

        @Test
        @DisplayName("移速足够时给出 ETA 与影响时段")
        void etaWhenMoving() {
            List<City> cities = Arrays.asList(
                    city(1L, "厦门", Integer.valueOf(4), Double.valueOf(20.05), Double.valueOf(120.0)));

            RiskAssessmentVO vo = assessWith(cities, circle(50, 200, 400),
                    Integer.valueOf(15), Double.valueOf(20D), 800D).get(0);

            assertNotNull(vo.getEta());
            assertNotNull(vo.getAffectedStart());
            assertNotNull(vo.getAffectedEnd());
        }

        @Test
        @DisplayName("移速为 0（停滞）时不给 ETA，避免除以近零移速")
        void stagnantHasNoEta() {
            List<City> cities = Arrays.asList(
                    city(1L, "厦门", Integer.valueOf(4), Double.valueOf(20.05), Double.valueOf(120.0)));

            RiskAssessmentVO vo = assessWith(cities, circle(50, 200, 400),
                    Integer.valueOf(15), Double.valueOf(0D), 800D).get(0);

            assertNull(vo.getEta());
            assertNull(vo.getAffectedEnd());
            assertNotNull(vo.getAffectedStart());
        }

        @Test
        @DisplayName("移速恰为 1 km/h（阈值边界）仍按停滞处理")
        void speedAtThresholdIsStagnant() {
            List<City> cities = Arrays.asList(
                    city(1L, "厦门", Integer.valueOf(4), Double.valueOf(20.05), Double.valueOf(120.0)));

            RiskAssessmentVO vo = assessWith(cities, circle(50, 200, 400),
                    Integer.valueOf(15), Double.valueOf(1D), 800D).get(0);

            assertNull(vo.getEta());
        }

        @Test
        @DisplayName("评估半径配置为 0 时回退默认 800 km，不产生除零")
        void zeroRangeFallsBackToDefault() {
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            Mockito.when(typhoonService.latestTrack(TYPHOON_ID))
                    .thenReturn(track(Double.valueOf(CENTER_LAT), Double.valueOf(CENTER_LON),
                            Integer.valueOf(15), Double.valueOf(20D)));
            Mockito.when(windCircleService.latest(TYPHOON_ID)).thenReturn(circle(50, 200, 400));
            // 第 1 次（筛选）给 800，之后的 evaluate 给 0 → 触发 rangeKmOrDefault 的兜底
            Mockito.when(settingDao.getDouble(Mockito.anyString(), Mockito.anyDouble()))
                    .thenReturn(Double.valueOf(800D), Double.valueOf(0D));
            Mockito.when(cityService.asMap()).thenReturn(asMap(Arrays.asList(
                    city(1L, "厦门", Integer.valueOf(4), Double.valueOf(20.05), Double.valueOf(120.0)))));

            List<RiskAssessmentVO> list = service.assess(TYPHOON_ID);

            assertEquals(1, list.size());
            assertNotNull(list.get(0).getRiskScore());
            assertEquals("EXTREME", list.get(0).getLevel());
        }
    }

    /* ==================================================================== */
    /*  assessAndSave / assessCity / clear                                   */
    /* ==================================================================== */

    @Nested
    @DisplayName("assessAndSave / assessCity / clear")
    class SaveAndSingleCity {

        private final List<City> cities = Arrays.asList(
                city(1L, "厦门", Integer.valueOf(4), Double.valueOf(20.05), Double.valueOf(120.0)),
                city(2L, "泉州", Integer.valueOf(3), Double.valueOf(21.0), Double.valueOf(120.0)));

        @Test
        @DisplayName("assessAndSave 先清旧记录再逐条落库")
        void savesAll() {
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            Mockito.when(typhoonService.latestTrack(TYPHOON_ID))
                    .thenReturn(track(Double.valueOf(CENTER_LAT), Double.valueOf(CENTER_LON),
                            Integer.valueOf(15), Double.valueOf(20D)));
            Mockito.when(windCircleService.latest(TYPHOON_ID)).thenReturn(circle(50, 200, 400));
            Mockito.when(settingDao.getDouble(Mockito.anyString(), Mockito.anyDouble()))
                    .thenReturn(Double.valueOf(800D));
            Mockito.when(cityService.asMap()).thenReturn(asMap(cities));
            Mockito.when(riskDao.deleteByTyphoon(TYPHOON_ID)).thenReturn(2);
            Mockito.when(riskDao.insert(Mockito.any(RiskAssessment.class))).thenReturn(1L);

            List<RiskAssessmentVO> list = service.assessAndSave(TYPHOON_ID, 7L);

            assertEquals(2, list.size());
            Mockito.verify(riskDao).deleteByTyphoon(TYPHOON_ID);
            Mockito.verify(riskDao, Mockito.times(2)).insert(Mockito.any(RiskAssessment.class));
        }

        @Test
        @DisplayName("没有可评估城市时只清旧记录，不写空记录")
        void savesNothingWhenEmpty() {
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            Mockito.when(typhoonService.latestTrack(TYPHOON_ID))
                    .thenReturn(track(Double.valueOf(CENTER_LAT), Double.valueOf(CENTER_LON),
                            Integer.valueOf(15), Double.valueOf(20D)));
            Mockito.when(windCircleService.latest(TYPHOON_ID)).thenReturn(null);
            Mockito.when(settingDao.getDouble(Mockito.anyString(), Mockito.anyDouble()))
                    .thenReturn(Double.valueOf(800D));
            Mockito.when(cityService.asMap()).thenReturn(asMap(Arrays.asList(
                    city(9L, "太远", Integer.valueOf(1), Double.valueOf(45.0), Double.valueOf(160.0)))));
            Mockito.when(riskDao.deleteByTyphoon(TYPHOON_ID)).thenReturn(0);

            assertTrue(service.assessAndSave(TYPHOON_ID, 7L).isEmpty());
            Mockito.verify(riskDao, Mockito.never()).insert(Mockito.any(RiskAssessment.class));
        }

        @Test
        @DisplayName("assessCity 命中推算结果时直接返回该城市")
        void singleCityFound() {
            Mockito.when(cityService.getById(1L)).thenReturn(cities.get(0));
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            Mockito.when(typhoonService.latestTrack(TYPHOON_ID))
                    .thenReturn(track(Double.valueOf(CENTER_LAT), Double.valueOf(CENTER_LON),
                            Integer.valueOf(15), Double.valueOf(20D)));
            Mockito.when(windCircleService.latest(TYPHOON_ID)).thenReturn(circle(50, 200, 400));
            Mockito.when(settingDao.getDouble(Mockito.anyString(), Mockito.anyDouble()))
                    .thenReturn(Double.valueOf(800D));
            Mockito.when(cityService.asMap()).thenReturn(asMap(cities));

            RiskAssessmentVO vo = service.assessCity(TYPHOON_ID, 1L);

            assertEquals(Long.valueOf(1L), vo.getCityId());
            assertEquals("EXTREME", vo.getLevel());
            assertEquals("厦门", vo.getCityName());
        }

        @Test
        @DisplayName("城市存在但超出评估半径 → 返回低风险占位对象而非 404")
        void singleCityOutOfRange() {
            Mockito.when(cityService.getById(3L))
                    .thenReturn(city(3L, "远处", Integer.valueOf(1), Double.valueOf(45.0), Double.valueOf(160.0)));
            Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
            Mockito.when(typhoonService.latestTrack(TYPHOON_ID))
                    .thenReturn(track(Double.valueOf(CENTER_LAT), Double.valueOf(CENTER_LON),
                            Integer.valueOf(15), Double.valueOf(20D)));
            Mockito.when(windCircleService.latest(TYPHOON_ID)).thenReturn(circle(50, 200, 400));
            Mockito.when(settingDao.getDouble(Mockito.anyString(), Mockito.anyDouble()))
                    .thenReturn(Double.valueOf(800D));
            Mockito.when(cityService.asMap()).thenReturn(asMap(cities));

            RiskAssessmentVO vo = service.assessCity(TYPHOON_ID, 3L);

            assertEquals("LOW", vo.getLevel());
            assertEquals("低", vo.getRiskLevel());
            assertEquals(0.0D, vo.getRiskScore().doubleValue(), 1e-9);
            assertFalse(vo.getInWindCircle().booleanValue());
            assertTrue(vo.getAdvice().contains("较远"));
        }

        @Test
        @DisplayName("城市不存在 → 404")
        void singleCityNotFound() {
            Mockito.when(cityService.getById(9L)).thenReturn(null);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> service.assessCity(TYPHOON_ID, 9L));
            assertEquals(Result.CODE_NOT_FOUND, e.getCode());
            assertTrue(e.getMessage().contains("城市不存在"));
        }

        @Test
        @DisplayName("clear 返回删除行数")
        void clearDelegates() {
            Mockito.when(riskDao.deleteByTyphoon(TYPHOON_ID)).thenReturn(4);
            assertEquals(4, service.clear(TYPHOON_ID));
        }
    }
}
