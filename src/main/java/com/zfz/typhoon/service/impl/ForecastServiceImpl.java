package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.ForecastAgencyDao;
import com.zfz.typhoon.dao.impl.ForecastAgencyDaoImpl;
import com.zfz.typhoon.dao.ForecastPathDao;
import com.zfz.typhoon.dao.impl.ForecastPathDaoImpl;
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
import com.zfz.typhoon.util.GeoUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 预报路径与机构业务实现。
 *
 * @author 追风者项目组
 */
public class ForecastServiceImpl implements ForecastService {

    /** 判定"该点位于某城市附近"的距离阈值（km）。
     *  取 220km：覆盖台风登陆点通常落在沿海城市周边 200km 内的气象实际。 */
    private static final double LANDING_MATCH_KM = 220d;

    private final ForecastPathDao pathDao;
    private final ForecastAgencyDao agencyDao;
    private final TyphoonService typhoonService;
    private final CityService cityService;

    /**
     * 生产用构造：按默认实现装配依赖。
     * <p>之所以把它拆成"无参 + 带参"两个构造，是为了给单元测试留出注入替身的缝隙：
     * 依赖全部在字段初始化里 new 出来的话，测试就必须连上真实 MySQL 才能跑，
     * 单元测试也就退化成了集成测试。
     */
    public ForecastServiceImpl() {
        this(new ForecastPathDaoImpl(), new ForecastAgencyDaoImpl(), new TyphoonServiceImpl(), new CityServiceImpl());
    }

    /**
     * 测试用构造：注入替身依赖（Mockito 生成的 mock 或手写桩）。
     * <p>参数顺序与上方字段声明一致，均为本服务运行所需的协作者。
     *
     * <p><b>为什么这里声明的是接口 {@link ForecastPathDao} 而不是实现类</b>：
     * Mockito 的 inline mock maker 在 JDK 25 上无法改写 {@code java.lang.Object}，
     * 因此<b>mock 具体类会直接失败</b>（错误形如
     * {@code Could not modify all classes [class java.lang.Object, ...]}）。
     * 而字段本身声明的就是接口 + 无参构造传的也是 {@code new ForecastPathDaoImpl()}，
     * 把参数类型收紧到实现类既没有任何收益，又堵死了单元测试 —— 已改回接口。
     */
    public ForecastServiceImpl(ForecastPathDao pathDao, ForecastAgencyDao agencyDao, TyphoonService typhoonService, CityService cityService) {
        this.pathDao = pathDao;
        this.agencyDao = agencyDao;
        this.typhoonService = typhoonService;
        this.cityService = cityService;
    }

    @Override
    public List<ForecastPathVO> listPaths(long typhoonId) {
        List<ForecastPath> rows = pathDao.listLatestByTyphoon(typhoonId);
        List<ForecastPathVO> result = new ArrayList<ForecastPathVO>();
        if (rows.isEmpty()) {
            return result;
        }

        double[] origin = currentPosition(typhoonId);
        LocalDateTime obsTime = obsTimeOf(typhoonId);

        // 按机构归组（LinkedHashMap 保持 DAO 返回的机构顺序）
        Map<String, List<ForecastPath>> grouped = new LinkedHashMap<String, List<ForecastPath>>();
        Map<String, ForecastAgency> agencyOf = new LinkedHashMap<String, ForecastAgency>();
        for (ForecastPath p : rows) {
            String code = p.getAgencyCode() == null ? "-" : p.getAgencyCode();
            List<ForecastPath> bucket = grouped.get(code);
            if (bucket == null) {
                bucket = new ArrayList<ForecastPath>();
                grouped.put(code, bucket);
            }
            bucket.add(p);

            if (!agencyOf.containsKey(code)) {
                ForecastAgency a = new ForecastAgency();
                a.setAgencyId(p.getAgencyId());
                a.setAgencyCode(p.getAgencyCode());
                a.setAgencyName(p.getAgencyName());
                a.setDisplayColor(p.getDisplayColor());
                agencyOf.put(code, a);
            }
        }

        for (Map.Entry<String, List<ForecastPath>> e : grouped.entrySet()) {
            String code = e.getKey();
            List<ForecastPath> points = e.getValue();
            ForecastAgency agency = agencyOf.get(code);

            ForecastPathVO vo = new ForecastPathVO();
            vo.setAgency(code);
            vo.setName(agency == null ? code : agency.getAgencyName());
            vo.setColor(agency == null ? "#1E90FF" : agency.getDisplayColor());
            vo.setDash(DEFAULT_DASH);

            // 起点校正：统一用当前实测位置，保证多机构路径从同一点分叉
            if (origin != null) {
                vo.addPoint(origin[0], origin[1]);
            }

            Integer endForce = null;
            LocalDateTime publishTime = null;
            for (ForecastPath p : points) {
                if (p.getForecastTime() == null || p.getLatitude() == null || p.getLongitude() == null) {
                    continue;
                }
                // 已过去（或恰在实测时刻）的预报点丢弃，避免与实况段重叠
                if (obsTime != null && !p.getForecastTime().isAfter(obsTime)) {
                    continue;
                }
                vo.addPoint(p.getLongitude().doubleValue(), p.getLatitude().doubleValue());
                endForce = p.getForecastWindForce();
                if (publishTime == null) {
                    publishTime = p.getPublishTime();
                }
            }

            // 若全部点都被实况覆盖，说明该机构最新预报尚未越过当前时刻，
            // 此时只保留起点，不画线（前端据此跳过渲染）
            vo.setEndWindForce(endForce);
            vo.setPublishTime(DateUtil.format(publishTime));
            vo.setLanding(landingText(vo.lastPoint()));
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<ForecastPath> listLatestRaw(long typhoonId) {
        return pathDao.listLatestByTyphoon(typhoonId);
    }

    @Override
    public List<ForecastPath> listLatestByAgency(long typhoonId, String agencyCode) {
        return pathDao.listLatestByTyphoonAndAgency(typhoonId, agencyCode);
    }

    @Override
    public Map<String, List<ForecastPath>> groupByAgency(long typhoonId) {
        Map<String, List<ForecastPath>> grouped = new LinkedHashMap<String, List<ForecastPath>>();
        for (ForecastPath p : pathDao.listLatestByTyphoon(typhoonId)) {
            String code = p.getAgencyCode() == null ? "-" : p.getAgencyCode();
            List<ForecastPath> bucket = grouped.get(code);
            if (bucket == null) {
                bucket = new ArrayList<ForecastPath>();
                grouped.put(code, bucket);
            }
            bucket.add(p);
        }
        return grouped;
    }

    @Override
    public List<AgencyVO> listAgencies() {
        List<ForecastAgency> list = agencyDao.listEnabled();
        List<AgencyVO> result = new ArrayList<AgencyVO>(list.size());
        for (ForecastAgency a : list) {
            result.add(AgencyVO.of(a));
        }
        return result;
    }

    @Override
    public int countByTyphoon(long typhoonId) {
        return pathDao.countByTyphoon(typhoonId);
    }

    @Override
    public double[] currentPosition(long typhoonId) {
        TyphoonTrack latest = typhoonService.latestTrack(typhoonId);
        if (latest == null || latest.getLongitude() == null || latest.getLatitude() == null) {
            return null;
        }
        return new double[]{latest.getLongitude().doubleValue(), latest.getLatitude().doubleValue()};
    }

    /* ------------------------------------------------------------------ */
    /*  内部实现                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * 取"当前实测时刻"（最后一条轨迹的观测时间），用于过滤已被实况覆盖的预报点。
     *
     * @param typhoonId 台风ID
     * @return 观测时间；无实况返回 null
     */
    private LocalDateTime obsTimeOf(long typhoonId) {
        TyphoonTrack latest = typhoonService.latestTrack(typhoonId);
        return latest == null ? null : latest.getObsTime();
    }

    /**
     * 由路径末点反推登陆点文案。
     *
     * @param lastPoint 末点 {@code [lon, lat]}，可为 null
     * @return 形如「厦门沿海」/「南平一带」；无法判定时返回 null（前端显示"—"）
     */
    private String landingText(List<Double> lastPoint) {
        if (lastPoint == null || lastPoint.size() < 2) {
            return null;
        }
        double lon = lastPoint.get(0).doubleValue();
        double lat = lastPoint.get(1).doubleValue();

        City nearest = null;
        double minKm = Double.MAX_VALUE;
        // 直接遍历缓存映射，避免转换 VO 的开销
        for (City c : cityService.asMap().values()) {
            if (c.getLatitude() == null || c.getLongitude() == null) {
                continue;
            }
            double km = GeoUtil.distanceKm(lat, lon, c.getLatitude().doubleValue(),
                    c.getLongitude().doubleValue());
            if (km < minKm) {
                minKm = km;
                nearest = c;
            }
        }

        // 末点离所有沿海城市都太远，说明预报终点在海上或内陆深处，
        // 此时不硬套城市名，直接给出方位描述，避免误导
        if (nearest == null || minKm > LANDING_MATCH_KM) {
            return null;
        }
        boolean coastal = nearest.getCoastal() != null && nearest.getCoastal().booleanValue();
        return nearest.getCityName() + (coastal ? "沿海" : "一带");
    }
}
