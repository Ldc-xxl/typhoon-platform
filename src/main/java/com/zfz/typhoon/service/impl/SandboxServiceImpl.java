package com.zfz.typhoon.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.zfz.typhoon.dao.SandboxScenarioDao;
import com.zfz.typhoon.dao.SystemSettingDao;
import com.zfz.typhoon.dao.TyphoonDao;
import com.zfz.typhoon.dao.impl.SandboxScenarioDaoImpl;
import com.zfz.typhoon.dao.impl.SystemSettingDaoImpl;
import com.zfz.typhoon.dao.impl.TyphoonDaoImpl;
import com.zfz.typhoon.model.City;
import com.zfz.typhoon.model.ForecastPath;
import com.zfz.typhoon.model.SandboxScenario;
import com.zfz.typhoon.model.Typhoon;
import com.zfz.typhoon.model.TyphoonTrack;
import com.zfz.typhoon.model.WindCircle;
import com.zfz.typhoon.model.dto.SandboxRequest;
import com.zfz.typhoon.model.vo.SandboxScenarioVO;
import com.zfz.typhoon.service.CityService;
import com.zfz.typhoon.service.ForecastService;
import com.zfz.typhoon.service.SandboxService;
import com.zfz.typhoon.service.TyphoonService;
import com.zfz.typhoon.service.WindCircleService;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.DateUtil;
import com.zfz.typhoon.util.GeoUtil;
import com.zfz.typhoon.util.JsonUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 情景沙盘推演实现。
 *
 * <p><b>四条推演规则（这是本模块全部的业务逻辑，刻意保持简单可验证）</b>
 *
 * <ol>
 *   <li><b>登陆点偏移</b>：把预报路径的末端沿<b>经度方向</b>平移
 *       {@code landingOffset} km，路径起点不动，中间点按"越靠近末端位移越大"
 *       线性插值。用经度方向而不是"垂直于路径方向"，是因为西北太平洋台风的
 *       登陆点差异主要就体现在经度上（同一纬度带内偏西 / 偏东登陆），
 *       这样定义直观、可验证，也不会出现符号取反导致"偏西变成偏东"的歧义。
 *       正值 = 偏东，负值 = 偏西。</li>
 *   <li><b>速度系数</b>：只影响<b>时间</b>（预计到达时间乘以系数），
 *       不影响几何形状——快慢不改变路径画在哪。</li>
 *   <li><b>强度增减</b>：直接叠加到台风中心风力上。</li>
 *   <li><b>风圈缩放</b>：四个象限的 7/10/12 级半径统一乘以系数。</li>
 * </ol>
 *
 * <p>受影响城市判定：对每个城市取它与推演路径所有点的<b>最小距离</b>，
 * 再用缩放后的风圈半径判断它落在哪一级风圈内，据此定风力等级与风险等级。
 *
 * @author 追风者项目组
 */
public class SandboxServiceImpl implements SandboxService {

    /** 参数默认值（用户不填时按基准情景处理） */
    private static final double DEFAULT_OFFSET = 0D;
    private static final double DEFAULT_SPEED_FACTOR = 1D;
    private static final double DEFAULT_INTENSITY_DELTA = 0D;
    private static final double DEFAULT_WIND_SCALE = 1D;

    /** 参数合法范围（与 system_settings.sandbox_param_range 一致，作为兜底） */
    private static final double OFFSET_MAX_KM = 120D;
    private static final double SPEED_FACTOR_MIN = 0.5D;
    private static final double SPEED_FACTOR_MAX = 1.5D;
    private static final double INTENSITY_DELTA_MAX = 3D;
    private static final double WIND_SCALE_MIN = 0.5D;
    private static final double WIND_SCALE_MAX = 2.0D;

    /** 风圈等级由高到低 */
    private static final int[] CIRCLE_LEVELS = {12, 10, 7};

    /** 超出风圈后每远离 60 km 递减 1 级 */
    private static final double FALLOFF_KM_PER_LEVEL = 60D;

    /** 风力等级上下限 */
    private static final double MIN_FORCE = 1D;
    private static final double MAX_FORCE = 17D;

    /** 风险等级分档阈值 */
    private static final double LEVEL_LOW_TO_MEDIUM = 35D;
    private static final double LEVEL_MEDIUM_TO_HIGH = 60D;
    private static final double LEVEL_HIGH_TO_EXTREME = 80D;

    /** 基准情景的机构（以中央气象台路径作为推演基底） */
    private static final String BASE_AGENCY = "CMA";

    /** 列表默认条数 */
    private static final int DEFAULT_LIMIT = 20;

    private final SandboxScenarioDao scenarioDao;
    private final SystemSettingDao settingDao;
    private final TyphoonDao typhoonDao;
    private final TyphoonService typhoonService;
    private final CityService cityService;
    private final ForecastService forecastService;
    private final WindCircleService windCircleService;

    /**
     * 生产用构造：按默认实现装配依赖。
     * <p>之所以把它拆成"无参 + 带参"两个构造，是为了给单元测试留出注入替身的缝隙：
     * 依赖全部在字段初始化里 new 出来的话，测试就必须连上真实 MySQL 才能跑，
     * 单元测试也就退化成了集成测试。
     */
    public SandboxServiceImpl() {
        this(new SandboxScenarioDaoImpl(), new SystemSettingDaoImpl(), new TyphoonDaoImpl(), new TyphoonServiceImpl(), new CityServiceImpl(), new ForecastServiceImpl(), new WindCircleServiceImpl());
    }

    /**
     * 测试用构造：注入替身依赖（Mockito 生成的 mock 或手写桩）。
     * <p>参数顺序与上方字段声明一致，均为本服务运行所需的协作者。
     */
    public SandboxServiceImpl(SandboxScenarioDao scenarioDao, SystemSettingDao settingDao, TyphoonDao typhoonDao, TyphoonService typhoonService, CityService cityService, ForecastService forecastService, WindCircleService windCircleService) {
        this.scenarioDao = scenarioDao;
        this.settingDao = settingDao;
        this.typhoonDao = typhoonDao;
        this.typhoonService = typhoonService;
        this.cityService = cityService;
        this.forecastService = forecastService;
        this.windCircleService = windCircleService;
    }

    /* ------------------------------------------------------------------ */
    /*  查询                                                                */
    /* ------------------------------------------------------------------ */

    @Override
    public List<SandboxScenarioVO> list(Long userId, Long baseTyphoonId, int limit) {
        int max = limit <= 0 ? DEFAULT_LIMIT : limit;
        List<SandboxScenario> rows;
        if (userId != null) {
            rows = baseTyphoonId == null
                    ? scenarioDao.listByUser(userId.longValue())
                    : scenarioDao.listByUserAndTyphoon(userId.longValue(), baseTyphoonId.longValue());
        } else if (baseTyphoonId != null) {
            rows = scenarioDao.listByBaseTyphoon(baseTyphoonId.longValue());
        } else {
            rows = scenarioDao.listPublic(max);
        }

        List<SandboxScenarioVO> result = new ArrayList<SandboxScenarioVO>();
        for (SandboxScenario s : rows) {
            if (result.size() >= max) {
                break;
            }
            result.add(toVO(s));
        }
        return result;
    }

    @Override
    public SandboxScenarioVO getById(long scenarioId) {
        return toVO(requireScenario(scenarioId));
    }

    /* ------------------------------------------------------------------ */
    /*  推演                                                                */
    /* ------------------------------------------------------------------ */

    @Override
    public SandboxScenarioVO simulate(SandboxRequest request) {
        if (request == null || request.getTyphoonId() == null) {
            throw BusinessException.badRequest("缺少必填字段：typhoonId");
        }
        long typhoonId = request.getTyphoonId().longValue();
        Typhoon typhoon = typhoonService.requireTyphoon(typhoonId);

        // 1) 基准路径：当前实测位置 + CMA 最新预报点
        List<double[]> basePath = basePath(typhoonId);
        if (basePath.size() < 2) {
            throw BusinessException.badRequest(
                    "该台风暂无可用预报路径，无法进行情景推演（需要至少 2 个预报点）");
        }

        // 2) 读取并校验参数（越界一律夹到合法区间，不直接报错，提升演示流畅度）
        double offset = clamp(value(request.getLandingOffset(), DEFAULT_OFFSET),
                -OFFSET_MAX_KM, OFFSET_MAX_KM);
        double speedFactor = clamp(value(request.getSpeedFactor(), DEFAULT_SPEED_FACTOR),
                SPEED_FACTOR_MIN, SPEED_FACTOR_MAX);
        double intensityDelta = clamp(value(request.getIntensityDelta(), DEFAULT_INTENSITY_DELTA),
                -INTENSITY_DELTA_MAX, INTENSITY_DELTA_MAX);
        double windScale = clamp(value(request.getWindScale(), DEFAULT_WIND_SCALE),
                WIND_SCALE_MIN, WIND_SCALE_MAX);

        // 3) 施加登陆点偏移
        List<double[]> shifted = shiftLanding(basePath, offset);

        // 4) 强度与风圈
        TyphoonTrack latest = typhoonService.latestTrack(typhoonId);
        int baseForce = latest != null && latest.getWindForce() != null
                ? latest.getWindForce().intValue() : 7;
        int scenarioForce = (int) clamp(baseForce + intensityDelta, MIN_FORCE, MAX_FORCE);
        WindCircle circle = windCircleService.latest(typhoonId);

        // 5) 重算受影响城市
        List<SandboxScenarioVO.CityImpact> cities = impacts(shifted, circle, windScale, scenarioForce);

        // 6) 组装结果
        SandboxScenarioVO vo = new SandboxScenarioVO();
        vo.setName(request.nameOrDefault());
        vo.setType(resolveType(request.getType(), offset));
        vo.setBaseTyphoonId(Long.valueOf(typhoonId));
        vo.setBaseTyphoonName(typhoon.getChineseName());
        vo.setCities(cities);
        vo.setCreatedAt(DateUtil.format(DateUtil.now()));
        vo.setNote(SandboxScenarioVO.NOTE);

        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("landingOffset", Double.valueOf(GeoUtil.round(offset, 1)));
        params.put("speedFactor", Double.valueOf(GeoUtil.round(speedFactor, 2)));
        params.put("intensityDelta", Double.valueOf(GeoUtil.round(intensityDelta, 1)));
        params.put("windScale", Double.valueOf(GeoUtil.round(windScale, 2)));
        params.put("path", shifted);
        vo.setParams(params);

        vo.setResult(buildResult(shifted, cities, scenarioForce, baseForce, speedFactor, windScale));
        vo.setTip(tipOf(cities, scenarioForce, baseForce));
        return vo;
    }

    @Override
    public SandboxScenarioVO save(long userId, SandboxRequest request) {
        SandboxScenarioVO vo = simulate(request);

        SandboxScenario entity = new SandboxScenario();
        entity.setUserId(Long.valueOf(userId));
        entity.setBaseTyphoonId(vo.getBaseTyphoonId());
        entity.setScenarioName(vo.getName());
        entity.setScenarioType(vo.getType());
        entity.setParamsJson((JsonNode) JsonUtil.mapper().valueToTree(vo.getParams()));
        entity.setResultJson((JsonNode) JsonUtil.mapper().valueToTree(snapshot(vo)));
        boolean shared = request.getShared() != null && request.getShared().booleanValue();
        entity.setPublicShared(Boolean.valueOf(shared));
        entity.setCreatedAt(DateUtil.now());
        entity.setUpdatedAt(DateUtil.now());

        long id = scenarioDao.insert(entity);
        vo.setId(Long.valueOf(id));
        return vo;
    }

    @Override
    public int setShared(long scenarioId, boolean shared) {
        requireScenario(scenarioId);
        return scenarioDao.setPublic(scenarioId, shared);
    }

    @Override
    public int delete(long scenarioId, long userId) {
        SandboxScenario s = requireScenario(scenarioId);
        if (s.getUserId() != null && s.getUserId().longValue() != userId) {
            throw BusinessException.forbidden("只能删除自己创建的情景方案");
        }
        return scenarioDao.deleteById(scenarioId);
    }

    @Override
    public Map<String, Object> paramRange() {
        String json = settingDao.getValue("sandbox_param_range", null);
        if (json != null && !json.trim().isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = JsonUtil.mapper().readValue(json, Map.class);
                if (parsed != null && !parsed.isEmpty()) {
                    return parsed;
                }
            } catch (Exception ignored) {
                // 配置被人工改坏时不能拖垮接口，退回下面的内置默认范围
            }
        }
        Map<String, Object> fallback = new LinkedHashMap<String, Object>();
        fallback.put("landing_offset_max_km", Double.valueOf(OFFSET_MAX_KM));
        fallback.put("speed_factor", range(SPEED_FACTOR_MIN, SPEED_FACTOR_MAX));
        fallback.put("intensity_offset_max", Double.valueOf(INTENSITY_DELTA_MAX));
        fallback.put("circle_scale", range(WIND_SCALE_MIN, WIND_SCALE_MAX));
        return fallback;
    }

    /* ------------------------------------------------------------------ */
    /*  内部：路径构造                                                       */
    /* ------------------------------------------------------------------ */

    /**
     * 取基准路径：当前实测位置作为起点 + 指定机构的最新预报点。
     *
     * <p>起点统一用实测位置，保证推演路径与页面上的预报路径从同一点出发
     * （与接口文档 3.6 节的业务约束一致）。
     *
     * @param typhoonId 台风ID
     * @return 路径点列表，每项 {@code [lon, lat]}
     */
    private List<double[]> basePath(long typhoonId) {
        List<double[]> path = new ArrayList<double[]>();

        double[] origin = forecastService.currentPosition(typhoonId);
        if (origin != null) {
            path.add(origin);
        }

        List<ForecastPath> points = forecastService.listLatestByAgency(typhoonId, BASE_AGENCY);
        if (points.isEmpty()) {
            // 该台风没有 CMA 预报时退回全部机构的最新点，避免整台沙盘不可用
            points = forecastService.listLatestRaw(typhoonId);
        }
        for (ForecastPath p : points) {
            if (p.getLongitude() == null || p.getLatitude() == null) {
                continue;
            }
            path.add(new double[]{p.getLongitude().doubleValue(), p.getLatitude().doubleValue()});
        }
        return path;
    }

    /**
     * 对路径施加登陆点横向偏移（沿经度方向）。
     *
     * @param path   基准路径，每项 {@code [lon, lat]}
     * @param offset 偏移量（km），正值偏东
     * @return 平移后的路径
     */
    private List<double[]> shiftLanding(List<double[]> path, double offset) {
        List<double[]> result = new ArrayList<double[]>(path.size());
        int lastIndex = path.size() - 1;
        for (int i = 0; i < path.size(); i++) {
            double[] p = path.get(i);
            // 起点位移为 0，末端位移为全量，中间线性过渡
            double ratio = lastIndex == 0 ? 0D : (double) i / lastIndex;
            double kmPerLon = GeoUtil.kmPerLon(p[1]);
            double dLon = kmPerLon <= 0D ? 0D : offset * ratio / kmPerLon;
            result.add(new double[]{GeoUtil.round(p[0] + dLon, 3), GeoUtil.round(p[1], 3)});
        }
        return result;
    }

    /* ------------------------------------------------------------------ */
    /*  内部：受影响城市                                                     */
    /* ------------------------------------------------------------------ */

    /**
     * 重算受影响城市清单。
     *
     * @param path          推演路径
     * @param circle        基准风圈；可为 null
     * @param windScale     风圈缩放系数
     * @param scenarioForce 情景下的台风中心风力
     * @return 城市影响列表，按风险分降序
     */
    private List<SandboxScenarioVO.CityImpact> impacts(List<double[]> path, WindCircle circle,
                                                       double windScale, int scenarioForce) {
        List<SandboxScenarioVO.CityImpact> list = new ArrayList<SandboxScenarioVO.CityImpact>();

        for (City city : cityService.asMap().values()) {
            if (city.getLatitude() == null || city.getLongitude() == null) {
                continue;
            }
            double cityLat = city.getLatitude().doubleValue();
            double cityLon = city.getLongitude().doubleValue();

            // 取城市到路径所有点的最小距离，以及最近点的象限
            double minKm = Double.MAX_VALUE;
            String quadrant = "ne";
            for (double[] p : path) {
                double km = GeoUtil.distanceKm(cityLat, cityLon, p[1], p[0]);
                if (km < minKm) {
                    minKm = km;
                    quadrant = GeoUtil.quadrant(p[1], p[0], cityLat, cityLon);
                }
            }

            int force = estimateForce(minKm, quadrant, circle, windScale, scenarioForce);
            double score = riskScore(force, minKm, city);
            String level = levelOf(score);

            SandboxScenarioVO.CityImpact impact = new SandboxScenarioVO.CityImpact();
            impact.setCityId(city.getCityId());
            impact.setCity(city.getCityName());
            impact.setProvince(city.getProvince());
            impact.setDistance(Double.valueOf(GeoUtil.round(minKm, 1)));
            impact.setExpectLevel(Integer.valueOf(force));
            impact.setRiskLevel(level);
            impact.setInCircle(Boolean.valueOf(force >= CIRCLE_LEVELS[CIRCLE_LEVELS.length - 1]));
            list.add(impact);
        }

        Collections.sort(list, new Comparator<SandboxScenarioVO.CityImpact>() {
            @Override
            public int compare(SandboxScenarioVO.CityImpact a, SandboxScenarioVO.CityImpact b) {
                // 距离近、风力大的排前面
                int fa = a.getExpectLevel() == null ? 0 : a.getExpectLevel().intValue();
                int fb = b.getExpectLevel() == null ? 0 : b.getExpectLevel().intValue();
                if (fa != fb) {
                    return Integer.compare(fb, fa);
                }
                double da = a.getDistance() == null ? Double.MAX_VALUE : a.getDistance().doubleValue();
                double db = b.getDistance() == null ? Double.MAX_VALUE : b.getDistance().doubleValue();
                return Double.compare(da, db);
            }
        });

        // 只保留受影响的（7 级及以上），避免把全国城市都塞给前端
        List<SandboxScenarioVO.CityImpact> affected = new ArrayList<SandboxScenarioVO.CityImpact>();
        for (SandboxScenarioVO.CityImpact c : list) {
            if (c.getExpectLevel() != null && c.getExpectLevel().intValue() >= 6) {
                affected.add(c);
            }
        }
        return affected;
    }

    /**
     * 估计城市将遭遇的风力等级。
     *
     * @param distance      到路径的最小距离（km）
     * @param quadrant      最近点处的象限
     * @param circle        基准风圈；可为 null
     * @param windScale     风圈缩放系数
     * @param scenarioForce 情景台风中心风力
     * @return 风力等级
     */
    private int estimateForce(double distance, String quadrant, WindCircle circle,
                              double windScale, int scenarioForce) {
        if (circle != null) {
            for (int level : CIRCLE_LEVELS) {
                double radius = circle.radius(level, quadrant) * windScale;
                if (radius > 0D && distance <= radius) {
                    return level == CIRCLE_LEVELS[0]
                            ? (int) clamp(Math.max(level, scenarioForce), MIN_FORCE, MAX_FORCE)
                            : level;
                }
            }
        }
        int decayed = (int) Math.round(scenarioForce - distance / FALLOFF_KM_PER_LEVEL);
        return (int) clamp(decayed, MIN_FORCE, MAX_FORCE);
    }

    /**
     * 风险评分（0—100）：风的影响 60 + 距离 25 + 城市脆弱性 15。
     *
     * @param force    预计风力
     * @param distance 距离（km）
     * @param city     城市
     * @return 风险评分
     */
    private double riskScore(int force, double distance, City city) {
        double windPart = 60D * Math.min(force / 17D, 1D);
        double distPart = 25D * clamp(1D - distance / 800D, 0D, 1D);
        double base = city.getBaseRiskLevel() == null ? 2D : city.getBaseRiskLevel().doubleValue();
        double vulnPart = 15D * clamp(base / 4D, 0D, 1D);
        return GeoUtil.round(clamp(windPart + distPart + vulnPart, 0D, 100D), 2);
    }

    /**
     * 风险评分 → 中文等级。
     *
     * @param score 风险评分
     * @return 低 / 中 / 高 / 极高
     */
    private String levelOf(double score) {
        if (score < LEVEL_LOW_TO_MEDIUM) {
            return "低";
        }
        if (score < LEVEL_MEDIUM_TO_HIGH) {
            return "中";
        }
        return score < LEVEL_HIGH_TO_EXTREME ? "高" : "极高";
    }

    /* ------------------------------------------------------------------ */
    /*  内部：结果快照                                                       */
    /* ------------------------------------------------------------------ */

    /**
     * 组织推演结果摘要（会把 cityCount / maxRisk 这类结论性数字给前端）。
     *
     * @param path          推演路径
     * @param cities        受影响城市
     * @param scenarioForce 情景台风中心风力
     * @param baseForce     基准台风中心风力
     * @param speedFactor   速度系数
     * @param windScale     风圈缩放系数
     * @return 结果摘要 Map
     */
    private Map<String, Object> buildResult(List<double[]> path,
                                            List<SandboxScenarioVO.CityImpact> cities,
                                            int scenarioForce, int baseForce,
                                            double speedFactor, double windScale) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("cityCount", Integer.valueOf(cities.size()));
        result.put("maxWindForce", Integer.valueOf(scenarioForce));
        result.put("baseWindForce", Integer.valueOf(baseForce));
        result.put("speedFactor", Double.valueOf(GeoUtil.round(speedFactor, 2)));
        result.put("windScale", Double.valueOf(GeoUtil.round(windScale, 2)));
        result.put("pathPoints", Integer.valueOf(path.size()));

        String maxRisk = "低";
        String maxRiskCity = null;
        for (SandboxScenarioVO.CityImpact c : cities) {
            if (rank(c.getRiskLevel()) > rank(maxRisk)) {
                maxRisk = c.getRiskLevel();
                maxRiskCity = c.getCity();
            }
        }
        result.put("maxRisk", maxRisk);
        result.put("maxRiskCity", maxRiskCity);
        result.put("note", SandboxScenarioVO.NOTE);
        return result;
    }

    /**
     * 把视图对象精简成可落库的快照（不含 disclaimer 之类的固定文案）。
     *
     * @param vo 推演结果
     * @return 快照 Map
     */
    private Map<String, Object> snapshot(SandboxScenarioVO vo) {
        Map<String, Object> snap = new LinkedHashMap<String, Object>();
        snap.put("result", vo.getResult());
        snap.put("cities", vo.getCities());
        snap.put("tip", vo.getTip());
        return snap;
    }

    /**
     * 生成一句人话提示，把参数改动的后果说出来。
     *
     * @param cities        受影响城市
     * @param scenarioForce 情景风力
     * @param baseForce     基准风力
     * @return 提示文案
     */
    private String tipOf(List<SandboxScenarioVO.CityImpact> cities, int scenarioForce, int baseForce) {
        StringBuilder sb = new StringBuilder();
        sb.append("本次推演共命中 ").append(cities.size()).append(" 个受影响城市");
        if (scenarioForce != baseForce) {
            sb.append("，中心风力由 ").append(baseForce).append(" 级变为 ")
                    .append(scenarioForce).append(" 级");
        }
        int extreme = 0;
        for (SandboxScenarioVO.CityImpact c : cities) {
            if ("极高".equals(c.getRiskLevel())) {
                extreme++;
            }
        }
        if (extreme > 0) {
            sb.append("，其中 ").append(extreme).append(" 个城市达到极高风险");
        }
        sb.append("。");
        return sb.toString();
    }

    /**
     * 按偏移量推断情景类型。
     *
     * @param explicit 显式指定的类型；非空即原样采用
     * @param offset   经度方向偏移（km）
     * @return BASELINE / SCENARIO_A / SCENARIO_B
     */
    private String resolveType(String explicit, double offset) {
        if (explicit != null && !explicit.trim().isEmpty()) {
            return explicit.trim().toUpperCase();
        }
        if (Math.abs(offset) < 1D) {
            return SandboxScenario.TYPE_BASELINE;
        }
        return offset < 0D ? SandboxScenario.TYPE_A : SandboxScenario.TYPE_B;
    }

    /**
     * 风险等级排序权重，用于取最严重的一档。
     *
     * @param level 中文等级
     * @return 权重
     */
    private int rank(String level) {
        if ("极高".equals(level)) {
            return 4;
        }
        if ("高".equals(level)) {
            return 3;
        }
        return "中".equals(level) ? 2 : 1;
    }

    /* ------------------------------------------------------------------ */
    /*  内部：实体 → VO                                                     */
    /* ------------------------------------------------------------------ */

    /**
     * 情景实体 → 视图对象。
     *
     * @param s 实体
     * @return 视图对象
     */
    private SandboxScenarioVO toVO(SandboxScenario s) {
        SandboxScenarioVO vo = new SandboxScenarioVO();
        vo.setId(s.getScenarioId());
        vo.setName(s.getScenarioName());
        vo.setType(s.getScenarioType());
        vo.setBaseTyphoonId(s.getBaseTyphoonId());
        vo.setCreatedAt(DateUtil.format(s.getCreatedAt()));
        vo.setNote(SandboxScenarioVO.NOTE);

        // 基底台风名：只取一个名字，不做完整的 VO 组装（TrackVO/WindCircle 那些都不需要）
        if (s.getBaseTyphoonId() != null) {
            Typhoon t = typhoonDao.findById(s.getBaseTyphoonId().longValue());
            vo.setBaseTyphoonName(t == null ? null : t.getChineseName());
        }

        Map<String, Object> params = toMap(s.getParamsJson());
        vo.setParams(params);

        Map<String, Object> result = toMap(s.getResultJson());
        if (result != null && result.get("result") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> inner = (Map<String, Object>) result.get("result");
            vo.setResult(inner);
        }
        if (result != null && result.get("tip") != null) {
            vo.setTip(String.valueOf(result.get("tip")));
        }
        return vo;
    }

    /**
     * JsonNode → Map。
     *
     * @param node JSON 节点
     * @return Map；节点为空时返回 null
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return JsonUtil.mapper().convertValue(node, Map.class);
        } catch (IllegalArgumentException e) {
            // JSON 列被人为改坏时不能让整个接口 500
            return null;
        }
    }

    /**
     * 取情景实体，不存在则抛 404。
     *
     * @param scenarioId 情景ID
     * @return 实体
     */
    private SandboxScenario requireScenario(long scenarioId) {
        SandboxScenario s = scenarioDao.findById(scenarioId);
        if (s == null) {
            throw BusinessException.notFound("情景方案不存在：id = " + scenarioId);
        }
        return s;
    }

    /* ------------------------------------------------------------------ */
    /*  小工具                                                              */
    /* ------------------------------------------------------------------ */

    /**
     * 取 Double 原始值。
     *
     * @param v 包装值
     * @param d 缺省值
     * @return 原始值
     */
    private double value(Double v, double d) {
        return v == null ? d : v.doubleValue();
    }

    /**
     * 构造二元区间。
     *
     * @param lo 下界
     * @param hi 上界
     * @return 区间列表
     */
    private List<Double> range(double lo, double hi) {
        List<Double> l = new ArrayList<Double>(2);
        l.add(Double.valueOf(lo));
        l.add(Double.valueOf(hi));
        return l;
    }

    /**
     * 区间截断。
     *
     * @param v  原始值
     * @param lo 下界
     * @param hi 上界
     * @return 截断结果
     */
    private double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
