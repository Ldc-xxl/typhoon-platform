package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.AiPredictionRecordDao;
import com.zfz.typhoon.dao.SystemSettingDao;
import com.zfz.typhoon.model.AiPredictionRecord;
import com.zfz.typhoon.model.City;
import com.zfz.typhoon.model.ForecastPath;
import com.zfz.typhoon.model.Typhoon;
import com.zfz.typhoon.model.vo.AiPredictionVO;
import com.zfz.typhoon.service.AiPredictionService;
import com.zfz.typhoon.service.CityService;
import com.zfz.typhoon.service.ForecastService;
import com.zfz.typhoon.service.TyphoonService;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.Result;
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
 * {@link AiPredictionServiceImpl} 单元测试。
 *
 * <p>被测对象是一条"规则化的多机构融合"流水线，最容易出错的三个地方：
 * <ol>
 *   <li><b>样本不足时的降级</b>——机构不足 2 家、或时刻无法对齐时，必须如实给出
 *       {@code PENDING} + 置信度 0.5，而不是拿半截数据编一条路径；</li>
 *   <li><b>置信度的量纲</b>——{@code consistency} 是 0—100 的整数，
 *       {@code confidence} 是 0—1 的小数（{@code consistency / 100}），
 *       且上限被钳到 95，不允许出现"100% 把握"；</li>
 *   <li><b>归一化参考离散度的兜底</b>——配置为 0 / 负数时不得产生除零，
 *       应回退到默认 400 km。</li>
 * </ol>
 *
 * <p>被测类在 {@link #setUp()} 中按"带参构造"手工装配，所有 {@code when(...)}
 * 一律写在测试方法体内，避免严格桩报"多余桩"。
 *
 * @author 追风者项目组
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AiPredictionServiceImpl · AI 路径融合测算")
class AiPredictionServiceImplTest {

    /** 测试台风ID */
    private static final long TYPHOON_ID = 1L;

    /** 默认置信度参考离散度（与被测类常量一致，用于桩） */
    private static final double DEFAULT_SCALE_KM = 400D;

    @Mock
    private AiPredictionRecordDao aiDao;

    @Mock
    private SystemSettingDao settingDao;

    @Mock
    private TyphoonService typhoonService;

    @Mock
    private ForecastService forecastService;

    @Mock
    private CityService cityService;

    private AiPredictionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiPredictionServiceImpl(aiDao, settingDao, typhoonService, forecastService, cityService);
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

    private ForecastPath point(double lat, double lon, Integer force, LocalDateTime time) {
        ForecastPath p = new ForecastPath();
        p.setLatitude(Double.valueOf(lat));
        p.setLongitude(Double.valueOf(lon));
        p.setForecastWindForce(force);
        p.setForecastTime(time);
        return p;
    }

    /**
     * 构造两家机构（CMA / JMA）的预报序列，各 3 个时刻。
     *
     * <p>第二家机构整体沿纬度平移 {@code deltaLat} 度，因此两家在每个时刻的
     * 两两距离恰好约为 {@code deltaLat × 111 km}，可以反过来精确控制离散度，
     * 从而命中置信度的各个分档。
     *
     * @param deltaLat 机构间纬度偏差（度）
     * @param f0       第 1 个时刻的风力
     * @param f1       第 2 个时刻的风力
     * @param f2       第 3 个时刻的风力
     * @return 机构代码 → 预报序列
     */
    private Map<String, List<ForecastPath>> twoAgencies(double deltaLat, int f0, int f1, int f2) {
        LocalDateTime t0 = LocalDateTime.now().plusHours(1);
        LocalDateTime t1 = t0.plusHours(6);
        LocalDateTime t2 = t0.plusHours(12);
        Map<String, List<ForecastPath>> grouped = new LinkedHashMap<String, List<ForecastPath>>();
        grouped.put("CMA", Arrays.asList(
                point(20.0, 120.0, Integer.valueOf(f0), t0),
                point(21.0, 121.0, Integer.valueOf(f1), t1),
                point(22.0, 122.0, Integer.valueOf(f2), t2)));
        grouped.put("JMA", Arrays.asList(
                point(20.0 + deltaLat, 120.0, Integer.valueOf(f0), t0),
                point(21.0 + deltaLat, 121.0, Integer.valueOf(f1), t1),
                point(22.0 + deltaLat, 122.0, Integer.valueOf(f2), t2)));
        return grouped;
    }

    private City city(long id, String name, String province, double lat, double lon, boolean coastal) {
        City c = new City();
        c.setCityId(Long.valueOf(id));
        c.setCityName(name);
        c.setProvince(province);
        c.setLatitude(Double.valueOf(lat));
        c.setLongitude(Double.valueOf(lon));
        c.setCoastal(Boolean.valueOf(coastal));
        return c;
    }

    /** 走"融合成功"分支所需的公共桩。 */
    private void stubFusion(Map<String, List<ForecastPath>> grouped, double scaleKm,
                            Map<Long, City> cityMap) {
        Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
        Mockito.when(forecastService.groupByAgency(TYPHOON_ID)).thenReturn(grouped);
        Mockito.when(settingDao.getDouble(AiPredictionService.KEY_CONFIDENCE_SCALE_KM,
                AiPredictionService.DEFAULT_CONFIDENCE_SCALE_KM)).thenReturn(scaleKm);
        Mockito.when(cityService.asMap()).thenReturn(cityMap);
        Mockito.when(aiDao.listLatestPerModel(TYPHOON_ID)).thenReturn(new ArrayList<AiPredictionRecord>());
    }

    /** 走"样本不足降级"分支所需的公共桩（不触达配置与城市）。 */
    private void stubDegraded(Map<String, List<ForecastPath>> grouped) {
        Mockito.when(typhoonService.requireTyphoon(TYPHOON_ID)).thenReturn(typhoon());
        Mockito.when(forecastService.groupByAgency(TYPHOON_ID)).thenReturn(grouped);
        Mockito.when(aiDao.listLatestPerModel(TYPHOON_ID)).thenReturn(new ArrayList<AiPredictionRecord>());
    }

    private Map<Long, City> emptyCities() {
        return new LinkedHashMap<Long, City>();
    }

    private void assertDegraded(AiPredictionVO vo) {
        assertEquals(Integer.valueOf(50), vo.getConsistency(), "降级时一致性固定 50");
        assertEquals("样本不足", vo.getConsistencyText());
        assertEquals(0.5D, vo.getConfidence().doubleValue(), 1e-9, "融合置信度是 0—1 量纲");
        assertEquals("PENDING", vo.getStatus());
    }

    /* ==================================================================== */
    /*  查询与清理                                                           */
    /* ==================================================================== */

    @Nested
    @DisplayName("listModels / clear · 明细与清理")
    class ListModelsAndClear {

        @Test
        @DisplayName("按模型明细组装：置信度四舍五入为 0—100 整数，状态固定「已接入」")
        void mapsModelItems() {
            AiPredictionRecord r = new AiPredictionRecord();
            r.setModelName("通义千问-max");
            r.setConfidenceScore(Double.valueOf(88.4));
            r.setPredictedLandfallLat(Double.valueOf(24.5));
            r.setPredictedLandfallLon(Double.valueOf(118.2));
            r.setDeviationVsAgencies(Double.valueOf(45.6));
            Mockito.when(aiDao.listLatestPerModel(TYPHOON_ID)).thenReturn(Arrays.asList(r));

            List<AiPredictionVO.ModelItem> items = service.listModels(TYPHOON_ID);

            assertEquals(1, items.size());
            assertEquals("通义千问-max", items.get(0).getName());
            assertEquals(Integer.valueOf(88), items.get(0).getConfidence());
            assertEquals("已接入", items.get(0).getStatus());
            assertEquals(Double.valueOf(118.2), items.get(0).getLandfallLon());
        }

        @Test
        @DisplayName("置信度为 null 时明细里保持 null，而不是编成 0")
        void nullConfidenceStaysNull() {
            AiPredictionRecord r = new AiPredictionRecord();
            r.setModelName("DeepSeek-V3");
            r.setConfidenceScore(null);
            Mockito.when(aiDao.listLatestPerModel(TYPHOON_ID)).thenReturn(Arrays.asList(r));

            assertNull(service.listModels(TYPHOON_ID).get(0).getConfidence());
        }

        @Test
        @DisplayName("无记录时返回空列表")
        void emptyList() {
            Mockito.when(aiDao.listLatestPerModel(TYPHOON_ID))
                    .thenReturn(new ArrayList<AiPredictionRecord>());
            assertTrue(service.listModels(TYPHOON_ID).isEmpty());
        }

        @Test
        @DisplayName("clear 直接返回 DAO 删除行数")
        void clearDelegatesToDao() {
            Mockito.when(aiDao.deleteByTyphoon(TYPHOON_ID)).thenReturn(3);
            assertEquals(3, service.clear(TYPHOON_ID));
        }
    }

    /* ==================================================================== */
    /*  台风校验                                                             */
    /* ==================================================================== */

    @Nested
    @DisplayName("latest / trigger · 台风校验")
    class TyphoonValidation {

        @Test
        @DisplayName("台风不存在时 latest 抛出 404，且不查预报")
        void latestNotFound() {
            Mockito.when(typhoonService.requireTyphoon(9L))
                    .thenThrow(BusinessException.notFound("台风不存在：id = 9"));

            BusinessException e = assertThrows(BusinessException.class, () -> service.latest(9L));
            assertEquals(Result.CODE_NOT_FOUND, e.getCode());
            Mockito.verifyNoInteractions(forecastService);
        }

        @Test
        @DisplayName("台风不存在时 trigger 抛出 404，且不落库")
        void triggerNotFound() {
            Mockito.when(typhoonService.requireTyphoon(9L))
                    .thenThrow(BusinessException.notFound("台风不存在：id = 9"));

            assertThrows(BusinessException.class, () -> service.trigger(9L, "DeepSeek-V3"));
            Mockito.verify(aiDao, Mockito.never()).insert(Mockito.any(AiPredictionRecord.class));
        }
    }

    /* ==================================================================== */
    /*  降级分支                                                             */
    /* ==================================================================== */

    @Nested
    @DisplayName("build · 样本不足降级")
    class Degraded {

        @Test
        @DisplayName("一家机构都没有时：PENDING + 说明文案")
        void noAgency() {
            stubDegraded(new LinkedHashMap<String, List<ForecastPath>>());

            AiPredictionVO vo = service.latest(TYPHOON_ID);

            assertDegraded(vo);
            assertEquals("PENDING", vo.getStatus());
            assertTrue(vo.getSummary().contains("不足 2 家"));
            assertEquals(Long.valueOf(TYPHOON_ID), vo.getTyphoonId());
        }

        @Test
        @DisplayName("只有一家机构时同样降级（无法计算离散度）")
        void singleAgency() {
            LocalDateTime t0 = LocalDateTime.now().plusHours(1);
            Map<String, List<ForecastPath>> grouped = new LinkedHashMap<String, List<ForecastPath>>();
            grouped.put("CMA", Arrays.asList(
                    point(20.0, 120.0, Integer.valueOf(10), t0),
                    point(21.0, 121.0, Integer.valueOf(11), t0.plusHours(6))));
            stubDegraded(grouped);

            assertDegraded(service.latest(TYPHOON_ID));
        }

        @Test
        @DisplayName("两家机构但预报时刻完全重合（时间轴只剩 1 点）→ 时刻无法对齐降级")
        void unalignableTimes() {
            LocalDateTime t0 = LocalDateTime.now().plusHours(1);
            Map<String, List<ForecastPath>> grouped = new LinkedHashMap<String, List<ForecastPath>>();
            grouped.put("CMA", Arrays.asList(
                    point(20.0, 120.0, Integer.valueOf(10), t0),
                    point(21.0, 121.0, Integer.valueOf(11), t0)));
            grouped.put("JMA", Arrays.asList(
                    point(20.5, 120.0, Integer.valueOf(10), t0),
                    point(21.5, 121.0, Integer.valueOf(11), t0)));
            stubDegraded(grouped);

            AiPredictionVO vo = service.latest(TYPHOON_ID);

            assertDegraded(vo);
            assertTrue(vo.getSummary().contains("无法对齐"));
        }

        @Test
        @DisplayName("点不足 2 个的机构被过滤掉，等同样本不足")
        void agencyWithTooFewPointsFiltered() {
            LocalDateTime t0 = LocalDateTime.now().plusHours(1);
            Map<String, List<ForecastPath>> grouped = new LinkedHashMap<String, List<ForecastPath>>();
            grouped.put("CMA", Arrays.asList(point(20.0, 120.0, Integer.valueOf(10), t0)));
            stubDegraded(grouped);

            assertDegraded(service.latest(TYPHOON_ID));
        }
    }

    /* ==================================================================== */
    /*  融合与置信度                                                         */
    /* ==================================================================== */

    @Nested
    @DisplayName("build · 融合路径与置信度")
    class Fusion {

        @Test
        @DisplayName("机构完全一致：一致性被钳到上限 95，置信度 0.95（0—1 量纲）")
        void identicalAgencies() {
            stubFusion(twoAgencies(0D, 10, 12, 8), DEFAULT_SCALE_KM, emptyCities());

            AiPredictionVO vo = service.latest(TYPHOON_ID);

            assertEquals(Integer.valueOf(95), vo.getConsistency());
            assertEquals("很好", vo.getConsistencyText());
            assertEquals(0.95D, vo.getConfidence().doubleValue(), 1e-9);
            assertEquals("VALIDATED", vo.getStatus());
            assertEquals(3, vo.getPoints().size());
            assertEquals(2, vo.getPoints().get(0).size());
            assertEquals("v1.1", vo.getVersion());
            assertEquals("#2BB8C4", vo.getColor());
            assertEquals("6 5", vo.getDash());
            assertNotNull(vo.getDisclaimer());
        }

        @Test
        @DisplayName("轻度离散（约 100 km）：一致性 75，评价「较好」")
        void slightSpread() {
            stubFusion(twoAgencies(0.9D, 10, 10, 10), DEFAULT_SCALE_KM, emptyCities());

            AiPredictionVO vo = service.latest(TYPHOON_ID);

            assertEquals(Integer.valueOf(75), vo.getConsistency());
            assertEquals("较好", vo.getConsistencyText());
            assertTrue(vo.getSummary().contains("分歧处于常见水平"));
        }

        @Test
        @DisplayName("中度离散（约 167 km）：一致性 58，评价「一般」")
        void mediumSpread() {
            stubFusion(twoAgencies(1.5D, 10, 10, 10), DEFAULT_SCALE_KM, emptyCities());

            AiPredictionVO vo = service.latest(TYPHOON_ID);

            assertEquals(Integer.valueOf(58), vo.getConsistency());
            assertEquals("一般", vo.getConsistencyText());
        }

        @Test
        @DisplayName("重度离散（约 334 km）：评价「较低」，文案提示分歧较大")
        void largeSpread() {
            stubFusion(twoAgencies(3.0D, 10, 10, 10), DEFAULT_SCALE_KM, emptyCities());

            AiPredictionVO vo = service.latest(TYPHOON_ID);

            assertEquals(Integer.valueOf(17), vo.getConsistency());
            assertEquals("较低", vo.getConsistencyText());
            assertTrue(vo.getSummary().contains("分歧较大"));
        }

        @Test
        @DisplayName("离散度超过参考值：归一化比值被钳到 1，一致性为 0（下边界）")
        void spreadExceedingScaleClampsToZero() {
            stubFusion(twoAgencies(4.0D, 10, 10, 10), DEFAULT_SCALE_KM, emptyCities());

            AiPredictionVO vo = service.latest(TYPHOON_ID);

            assertEquals(Integer.valueOf(0), vo.getConsistency());
            assertEquals(0.0D, vo.getConfidence().doubleValue(), 1e-9);
            assertEquals("较低", vo.getConsistencyText());
        }

        @Test
        @DisplayName("参考离散度配置为 0 时回退默认 400 km，不产生除零")
        void zeroScaleFallsBackToDefault() {
            stubFusion(twoAgencies(0D, 10, 10, 10), 0D, emptyCities());

            AiPredictionVO vo = service.latest(TYPHOON_ID);

            assertEquals(Integer.valueOf(95), vo.getConsistency());
        }

        @Test
        @DisplayName("强度趋势：峰值 ≥ 12 级且末段回落 → 逐步减弱 + 强台风级提示")
        void trendWeakens() {
            stubFusion(twoAgencies(0D, 10, 12, 8), DEFAULT_SCALE_KM, emptyCities());

            String trend = service.latest(TYPHOON_ID).getIntensityTrend();

            assertTrue(trend.contains("逐步减弱"));
            assertTrue(trend.contains("强台风级及以上"));
        }

        @Test
        @DisplayName("强度趋势：末段高于起点 → 持续增强")
        void trendStrengthens() {
            stubFusion(twoAgencies(0D, 8, 8, 12), DEFAULT_SCALE_KM, emptyCities());

            assertTrue(service.latest(TYPHOON_ID).getIntensityTrend().contains("持续增强"));
        }

        @Test
        @DisplayName("强度趋势：起末持平 → 总体维持，且峰值未达 12 级时不加「强台风」字样")
        void trendMaintains() {
            stubFusion(twoAgencies(0D, 5, 6, 5), DEFAULT_SCALE_KM, emptyCities());

            String trend = service.latest(TYPHOON_ID).getIntensityTrend();

            assertTrue(trend.contains("逐步减弱"));
            assertFalse(trend.contains("强台风级及以上"));
        }
    }

    /* ==================================================================== */
    /*  登陆点与落库                                                         */
    /* ==================================================================== */

    @Nested
    @DisplayName("build · 登陆点文案与落库")
    class LandingAndSave {

        @Test
        @DisplayName("末点落在沿海城市附近 → 「城市名 + 沿海」")
        void coastalLanding() {
            Map<Long, City> cities = new LinkedHashMap<Long, City>();
            cities.put(Long.valueOf(1L), city(1L, "厦门", "福建", 22.0, 122.0, true));
            stubFusion(twoAgencies(0D, 10, 10, 10), DEFAULT_SCALE_KM, cities);

            assertEquals("厦门沿海", service.latest(TYPHOON_ID).getLandingRange());
        }

        @Test
        @DisplayName("末点附近是非沿海城市 → 「城市名 + 一带」")
        void inlandLanding() {
            Map<Long, City> cities = new LinkedHashMap<Long, City>();
            cities.put(Long.valueOf(2L), city(2L, "南平", "福建", 22.0, 122.0, false));
            stubFusion(twoAgencies(0D, 10, 10, 10), DEFAULT_SCALE_KM, cities);

            assertEquals("南平一带", service.latest(TYPHOON_ID).getLandingRange());
        }

        @Test
        @DisplayName("最近城市也超过 220 km → 退化为经纬度海域描述")
        void farFromAnyCity() {
            Map<Long, City> cities = new LinkedHashMap<Long, City>();
            cities.put(Long.valueOf(3L), city(3L, "远城", "远方", 45.0, 150.0, true));
            stubFusion(twoAgencies(0D, 10, 10, 10), DEFAULT_SCALE_KM, cities);

            String landing = service.latest(TYPHOON_ID).getLandingRange();

            assertTrue(landing.contains("°N"));
            assertTrue(landing.contains("附近海域"));
        }

        @Test
        @DisplayName("latest 只算不存：不调用 insert")
        void latestDoesNotPersist() {
            stubFusion(twoAgencies(0D, 10, 10, 10), DEFAULT_SCALE_KM, emptyCities());

            service.latest(TYPHOON_ID);

            Mockito.verify(aiDao, Mockito.never()).insert(Mockito.any(AiPredictionRecord.class));
        }

        @Test
        @DisplayName("trigger 指定模型名：按该名称落库")
        void triggerSavesGivenModel() {
            stubFusion(twoAgencies(0D, 10, 10, 10), DEFAULT_SCALE_KM, emptyCities());
            Mockito.when(aiDao.insert(Mockito.any(AiPredictionRecord.class))).thenReturn(7L);

            AiPredictionVO vo = service.trigger(TYPHOON_ID, "通义千问-max");

            ArgumentCaptor<AiPredictionRecord> captor =
                    ArgumentCaptor.forClass(AiPredictionRecord.class);
            Mockito.verify(aiDao).insert(captor.capture());
            AiPredictionRecord record = captor.getValue();
            assertEquals("通义千问-max", record.getModelName());
            assertEquals("VALIDATED", record.getStatus());
            assertEquals(Long.valueOf(TYPHOON_ID), record.getTyphoonId());
            assertEquals(Double.valueOf(95D), record.getConfidenceScore(),
                    "落库置信度应是 0—100 的一致性");
            assertNotNull(record.getPredictedPath());
            assertNotNull(record.getCreatedAt());
            assertEquals("VALIDATED", vo.getStatus());
        }

        @Test
        @DisplayName("trigger 未传模型名：空白一律回退默认模型 DeepSeek-V3")
        void triggerUsesDefaultModel() {
            stubFusion(twoAgencies(0D, 10, 10, 10), DEFAULT_SCALE_KM, emptyCities());
            Mockito.when(aiDao.insert(Mockito.any(AiPredictionRecord.class))).thenReturn(1L);

            service.trigger(TYPHOON_ID, "   ");

            ArgumentCaptor<AiPredictionRecord> captor =
                    ArgumentCaptor.forClass(AiPredictionRecord.class);
            Mockito.verify(aiDao).insert(captor.capture());
            assertEquals("DeepSeek-V3", captor.getValue().getModelName());
        }

        @Test
        @DisplayName("trigger 在样本不足时降级且不落库")
        void triggerDegradedDoesNotPersist() {
            stubDegraded(new LinkedHashMap<String, List<ForecastPath>>());

            AiPredictionVO vo = service.trigger(TYPHOON_ID, "DeepSeek-V3");

            assertDegraded(vo);
            Mockito.verify(aiDao, Mockito.never()).insert(Mockito.any(AiPredictionRecord.class));
        }
    }
}
