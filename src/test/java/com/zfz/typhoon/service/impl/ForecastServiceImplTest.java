package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.ForecastAgencyDao;
import com.zfz.typhoon.dao.ForecastPathDao;
import com.zfz.typhoon.model.City;
import com.zfz.typhoon.model.ForecastAgency;
import com.zfz.typhoon.model.ForecastPath;
import com.zfz.typhoon.model.TyphoonTrack;
import com.zfz.typhoon.model.vo.AgencyVO;
import com.zfz.typhoon.model.vo.ForecastPathVO;
import com.zfz.typhoon.service.CityService;
import com.zfz.typhoon.service.ForecastService;
import com.zfz.typhoon.service.TyphoonService;
import com.zfz.typhoon.util.DateUtil;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ForecastServiceImpl} 单元测试。
 *
 * <p>本服务把"各机构最新预报点"组装成前端可画的折线，关键在于：
 * <ol>
 *   <li><b>起点校正</b>——所有机构共用当前实测位置作为第一个点；</li>
 *   <li><b>已过时预报点过滤</b>——预报时刻不晚于实测时刻的点被丢弃；</li>
 *   <li><b>登陆点反推</b>——由末点取最近城市，沿海 / 内陆文案不同。</li>
 * </ol>
 *
 * <h3>关于依赖类型</h3>
 * 替身一律建立在<b>接口</b> {@link ForecastPathDao} 上：JDK 25 的 Mockito
 * 无法改写 {@code java.lang.Object}，mock 具体实现类会直接抛
 * {@code Could not modify all classes [...]}。为此生产代码的带参构造
 * 也已把参数类型从 {@code ForecastPathDaoImpl} 放宽回接口。
 *
 * @author 追风者项目组
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ForecastServiceImpl · 多机构预报路径")
class ForecastServiceImplTest {

    /** 当前实测时刻，作为所有时间偏移的基准 */
    private static final LocalDateTime OBS = LocalDateTime.of(2026, 9, 1, 8, 0, 0);

    @Mock
    private ForecastPathDao pathDao;

    @Mock
    private ForecastAgencyDao agencyDao;

    @Mock
    private TyphoonService typhoonService;

    @Mock
    private CityService cityService;

    private ForecastServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ForecastServiceImpl(pathDao, agencyDao, typhoonService, cityService);
    }

    /* ------------------------------------------------------------------ */

    private ForecastPath path(String code, String name, String color,
                              LocalDateTime forecastTime, Double lat, Double lon,
                              Integer force, LocalDateTime publishTime) {
        ForecastPath p = new ForecastPath();
        p.setTyphoonId(Long.valueOf(1L));
        p.setAgencyId(Long.valueOf(1L));
        p.setAgencyCode(code);
        p.setAgencyName(name);
        p.setDisplayColor(color);
        p.setForecastTime(forecastTime);
        p.setLatitude(lat);
        p.setLongitude(lon);
        p.setForecastWindForce(force);
        p.setPublishTime(publishTime);
        return p;
    }

    private TyphoonTrack track(Double lon, Double lat, LocalDateTime obsTime) {
        TyphoonTrack t = new TyphoonTrack();
        t.setTyphoonId(Long.valueOf(1L));
        t.setLongitude(lon);
        t.setLatitude(lat);
        t.setObsTime(obsTime);
        return t;
    }

    private City city(long id, String name, boolean coastal, double lat, double lon) {
        City c = new City();
        c.setCityId(Long.valueOf(id));
        c.setCityName(name);
        c.setProvince("福建");
        c.setCoastal(Boolean.valueOf(coastal));
        c.setLatitude(Double.valueOf(lat));
        c.setLongitude(Double.valueOf(lon));
        return c;
    }

    /* ==================================================================== */
    /*  listPaths                                                           */
    /* ==================================================================== */

    @Nested
    @DisplayName("listPaths · 组装多机构路径")
    class ListPaths {

        @Test
        @DisplayName("无预报数据：返回空列表，不查实况")
        void emptyRows() {
            Mockito.when(pathDao.listLatestByTyphoon(1L)).thenReturn(new ArrayList<ForecastPath>());

            assertTrue(service.listPaths(1L).isEmpty());
            Mockito.verifyNoInteractions(typhoonService);
        }

        @Test
        @DisplayName("起点校正 + 末点反推登陆点（沿海城市）")
        void originAndLanding() {
            LocalDateTime publish = OBS.plusHours(1);
            Mockito.when(typhoonService.latestTrack(1L)).thenReturn(track(Double.valueOf(118.0), Double.valueOf(24.0), OBS));
            Mockito.when(pathDao.listLatestByTyphoon(1L)).thenReturn(Collections.singletonList(
                    path("CMA", "中央气象台", "#E60012", OBS.plusHours(12),
                            Double.valueOf(24.5), Double.valueOf(118.5), Integer.valueOf(12), publish)));
            Map<Long, City> cities = new LinkedHashMap<Long, City>();
            cities.put(Long.valueOf(1L), city(1, "厦门", true, 24.46, 118.1));
            Mockito.when(cityService.asMap()).thenReturn(cities);

            List<ForecastPathVO> list = service.listPaths(1L);

            assertEquals(1, list.size());
            ForecastPathVO vo = list.get(0);
            assertEquals("CMA", vo.getAgency());
            assertEquals("中央气象台", vo.getName());
            assertEquals("#E60012", vo.getColor());
            assertEquals(ForecastService.DEFAULT_DASH, vo.getDash());
            assertEquals(2, vo.getPoints().size());
            // 第一个点必须被校正为当前实测位置 [经度, 纬度]
            assertEquals(Double.valueOf(118.0), vo.getPoints().get(0).get(0));
            assertEquals(Double.valueOf(24.0), vo.getPoints().get(0).get(1));
            assertEquals(Double.valueOf(118.5), vo.getPoints().get(1).get(0));
            assertEquals(Integer.valueOf(12), vo.getEndWindForce());
            assertEquals(DateUtil.format(publish), vo.getPublishTime());
            assertEquals("厦门沿海", vo.getLanding());
        }

        @Test
        @DisplayName("内陆城市：登陆点文案用「一带」")
        void inlandLanding() {
            Mockito.when(typhoonService.latestTrack(1L)).thenReturn(track(Double.valueOf(118.0), Double.valueOf(24.0), OBS));
            Mockito.when(pathDao.listLatestByTyphoon(1L)).thenReturn(Collections.singletonList(
                    path("CMA", "中央气象台", "#E60012", OBS.plusHours(12),
                            Double.valueOf(26.6), Double.valueOf(118.2), Integer.valueOf(10), OBS)));
            Map<Long, City> cities = new LinkedHashMap<Long, City>();
            cities.put(Long.valueOf(3L), city(3, "南平", false, 26.64, 118.18));
            Mockito.when(cityService.asMap()).thenReturn(cities);

            assertEquals("南平一带", service.listPaths(1L).get(0).getLanding());
        }

        @Test
        @DisplayName("末点离所有城市都很远：不硬套城市名，landing 为 null")
        void landingNullWhenFarAway() {
            Mockito.when(typhoonService.latestTrack(1L)).thenReturn(track(Double.valueOf(150.0), Double.valueOf(10.0), OBS));
            Mockito.when(pathDao.listLatestByTyphoon(1L)).thenReturn(Collections.singletonList(
                    path("CMA", "中央气象台", "#E60012", OBS.plusHours(12),
                            Double.valueOf(10.0), Double.valueOf(150.0), Integer.valueOf(10), OBS)));
            Mockito.when(cityService.asMap()).thenReturn(new LinkedHashMap<Long, City>());

            assertNull(service.listPaths(1L).get(0).getLanding());
        }

        @Test
        @DisplayName("预报时刻不晚于实测时刻的点被丢弃（只留起点）")
        void filtersPointsCoveredByObs() {
            Mockito.when(typhoonService.latestTrack(1L)).thenReturn(track(Double.valueOf(118.0), Double.valueOf(24.0), OBS));
            Mockito.when(pathDao.listLatestByTyphoon(1L)).thenReturn(Collections.singletonList(
                    path("CMA", "中央气象台", "#E60012", OBS.minusHours(6),
                            Double.valueOf(23.5), Double.valueOf(117.5), Integer.valueOf(8), OBS)));
            Mockito.when(cityService.asMap()).thenReturn(new LinkedHashMap<Long, City>());

            ForecastPathVO vo = service.listPaths(1L).get(0);

            assertEquals(1, vo.getPoints().size(), "只应保留被校正后的起点");
            assertNull(vo.getEndWindForce());
        }

        @Test
        @DisplayName("预报时刻恰好等于实测时刻也被丢弃（边界）")
        void filterAtExactObsTime() {
            Mockito.when(typhoonService.latestTrack(1L)).thenReturn(track(Double.valueOf(118.0), Double.valueOf(24.0), OBS));
            Mockito.when(pathDao.listLatestByTyphoon(1L)).thenReturn(Collections.singletonList(
                    path("CMA", "中央气象台", "#E60012", OBS,
                            Double.valueOf(23.5), Double.valueOf(117.5), Integer.valueOf(8), OBS)));
            Mockito.when(cityService.asMap()).thenReturn(new LinkedHashMap<Long, City>());

            assertEquals(1, service.listPaths(1L).get(0).getPoints().size());
        }

        @Test
        @DisplayName("坐标或预报时刻缺失的点被跳过")
        void skipsIncompletePoints() {
            Mockito.when(typhoonService.latestTrack(1L)).thenReturn(track(Double.valueOf(118.0), Double.valueOf(24.0), OBS));
            ForecastPath noTime = path("CMA", "中央气象台", "#E60012", null,
                    Double.valueOf(23.0), Double.valueOf(117.0), Integer.valueOf(8), OBS);
            ForecastPath noLat = path("CMA", "中央气象台", "#E60012", OBS.plusHours(6),
                    null, Double.valueOf(117.0), Integer.valueOf(8), OBS);
            ForecastPath ok = path("CMA", "中央气象台", "#E60012", OBS.plusHours(12),
                    Double.valueOf(25.0), Double.valueOf(119.0), Integer.valueOf(11), OBS);
            Mockito.when(pathDao.listLatestByTyphoon(1L)).thenReturn(Arrays.asList(noTime, noLat, ok));
            Mockito.when(cityService.asMap()).thenReturn(new LinkedHashMap<Long, City>());

            ForecastPathVO vo = service.listPaths(1L).get(0);

            assertEquals(2, vo.getPoints().size(), "起点 + 唯一完整的预报点");
            assertEquals(Integer.valueOf(11), vo.getEndWindForce());
        }

        @Test
        @DisplayName("无实况时不起校点，路径仅含预报点；obsTime 为 null 因而不过滤")
        void noOriginNoFiltering() {
            Mockito.when(typhoonService.latestTrack(1L)).thenReturn(null);
            Mockito.when(pathDao.listLatestByTyphoon(1L)).thenReturn(Collections.singletonList(
                    path("CMA", "中央气象台", "#E60012", OBS.minusHours(3),
                            Double.valueOf(23.5), Double.valueOf(117.5), Integer.valueOf(8), OBS)));
            Mockito.when(cityService.asMap()).thenReturn(new LinkedHashMap<Long, City>());

            ForecastPathVO vo = service.listPaths(1L).get(0);

            assertEquals(1, vo.getPoints().size());
            assertEquals(Double.valueOf(117.5), vo.getPoints().get(0).get(0));
            assertNull(vo.getLanding(), "空城市映射时无法反推登陆点");
        }

        @Test
        @DisplayName("多机构按机构分组，保持 DAO 返回顺序")
        void groupsByAgency() {
            Mockito.when(typhoonService.latestTrack(1L)).thenReturn(track(Double.valueOf(118.0), Double.valueOf(24.0), OBS));
            Mockito.when(pathDao.listLatestByTyphoon(1L)).thenReturn(Arrays.asList(
                    path("CMA", "中央气象台", "#E60012", OBS.plusHours(6),
                            Double.valueOf(24.2), Double.valueOf(118.2), Integer.valueOf(10), OBS),
                    path("JMA", "日本气象厅", "#1E90FF", OBS.plusHours(6),
                            Double.valueOf(24.3), Double.valueOf(118.3), Integer.valueOf(10), OBS),
                    path("CMA", "中央气象台", "#E60012", OBS.plusHours(12),
                            Double.valueOf(24.4), Double.valueOf(118.4), Integer.valueOf(11), OBS)));
            Mockito.when(cityService.asMap()).thenReturn(new LinkedHashMap<Long, City>());

            List<ForecastPathVO> list = service.listPaths(1L);

            assertEquals(2, list.size());
            assertEquals("CMA", list.get(0).getAgency());
            assertEquals(3, list.get(0).getPoints().size(), "起点 + 2 个 CMA 预报点");
            assertEquals("JMA", list.get(1).getAgency());
            assertEquals("#1E90FF", list.get(1).getColor());
        }

        @Test
        @DisplayName("agencyCode 为 null 时归到「-」分组")
        void nullAgencyCode() {
            Mockito.when(typhoonService.latestTrack(1L)).thenReturn(track(Double.valueOf(118.0), Double.valueOf(24.0), OBS));
            Mockito.when(pathDao.listLatestByTyphoon(1L)).thenReturn(Collections.singletonList(
                    path(null, null, null, OBS.plusHours(6),
                            Double.valueOf(24.2), Double.valueOf(118.2), Integer.valueOf(10), OBS)));
            Mockito.when(cityService.asMap()).thenReturn(new LinkedHashMap<Long, City>());

            ForecastPathVO vo = service.listPaths(1L).get(0);

            assertEquals("-", vo.getAgency());
            assertNotNull(vo.getPoints());
        }
    }

    /* ==================================================================== */
    /*  原始查询                                                            */
    /* ==================================================================== */

    @Nested
    @DisplayName("原始查询 · listLatestRaw / listLatestByAgency / groupByAgency / count")
    class RawQueries {

        @Test
        @DisplayName("listLatestRaw 委托 DAO")
        void listLatestRaw() {
            List<ForecastPath> rows = Collections.singletonList(new ForecastPath());
            Mockito.when(pathDao.listLatestByTyphoon(1L)).thenReturn(rows);

            assertEquals(rows, service.listLatestRaw(1L));
        }

        @Test
        @DisplayName("listLatestByAgency 委托 DAO")
        void listLatestByAgency() {
            Mockito.when(pathDao.listLatestByTyphoonAndAgency(1L, "CMA"))
                    .thenReturn(Collections.singletonList(new ForecastPath()));

            assertEquals(1, service.listLatestByAgency(1L, "CMA").size());
        }

        @Test
        @DisplayName("groupByAgency 按机构代码归组，null 归「-」")
        void groupByAgency() {
            Mockito.when(pathDao.listLatestByTyphoon(1L)).thenReturn(Arrays.asList(
                    path("CMA", "中央气象台", "#E60012", OBS, Double.valueOf(24.0), Double.valueOf(118.0), Integer.valueOf(10), OBS),
                    path(null, null, null, OBS, Double.valueOf(24.0), Double.valueOf(118.0), Integer.valueOf(10), OBS)));

            Map<String, List<ForecastPath>> grouped = service.groupByAgency(1L);

            assertEquals(2, grouped.size());
            assertEquals(1, grouped.get("CMA").size());
            assertEquals(1, grouped.get("-").size());
        }

        @Test
        @DisplayName("countByTyphoon 委托 DAO")
        void countByTyphoon() {
            Mockito.when(pathDao.countByTyphoon(1L)).thenReturn(7);

            assertEquals(7, service.countByTyphoon(1L));
        }
    }

    /* ==================================================================== */
    /*  机构清单与当前位置                                                  */
    /* ==================================================================== */

    @Nested
    @DisplayName("listAgencies / currentPosition")
    class AgenciesAndPosition {

        @Test
        @DisplayName("listAgencies 把启用的机构转成视图（ECMWF 标为集合预报）")
        void listAgencies() {
            ForecastAgency cma = new ForecastAgency();
            cma.setAgencyId(Long.valueOf(1L));
            cma.setAgencyCode("CMA");
            cma.setAgencyName("中央气象台");
            ForecastAgency ecmwf = new ForecastAgency();
            ecmwf.setAgencyId(Long.valueOf(2L));
            ecmwf.setAgencyCode("ecmwf");
            ecmwf.setAgencyName("欧洲中期预报中心");
            Mockito.when(agencyDao.listEnabled()).thenReturn(Arrays.asList(cma, ecmwf));

            List<AgencyVO> list = service.listAgencies();

            assertEquals(2, list.size());
            assertEquals("REST API", list.get(0).getType());
            assertEquals("集合预报", list.get(1).getType());
        }

        @Test
        @DisplayName("listAgencies 空列表返回空")
        void listAgenciesEmpty() {
            Mockito.when(agencyDao.listEnabled()).thenReturn(new ArrayList<ForecastAgency>());

            assertTrue(service.listAgencies().isEmpty());
        }

        @Test
        @DisplayName("currentPosition：有实况返回 [经度, 纬度]")
        void currentPosition() {
            Mockito.when(typhoonService.latestTrack(1L))
                    .thenReturn(track(Double.valueOf(118.1), Double.valueOf(24.5), OBS));

            double[] pos = service.currentPosition(1L);

            assertEquals(118.1D, pos[0], 1e-9);
            assertEquals(24.5D, pos[1], 1e-9);
        }

        @Test
        @DisplayName("currentPosition：无实况返回 null")
        void currentPositionNoTrack() {
            Mockito.when(typhoonService.latestTrack(1L)).thenReturn(null);

            assertNull(service.currentPosition(1L));
        }

        @Test
        @DisplayName("currentPosition：实况缺经纬度返回 null")
        void currentPositionMissingCoords() {
            Mockito.when(typhoonService.latestTrack(1L)).thenReturn(track(null, Double.valueOf(24.5), OBS));

            assertNull(service.currentPosition(1L));
        }
    }
}
