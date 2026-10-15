package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.CityDao;
import com.zfz.typhoon.dao.RiskAssessmentDao;
import com.zfz.typhoon.dao.SystemSettingDao;
import com.zfz.typhoon.dao.impl.CityDaoImpl;
import com.zfz.typhoon.dao.impl.RiskAssessmentDaoImpl;
import com.zfz.typhoon.dao.impl.SystemSettingDaoImpl;
import com.zfz.typhoon.model.City;
import com.zfz.typhoon.model.RiskAssessment;
import com.zfz.typhoon.model.TyphoonTrack;
import com.zfz.typhoon.model.WindCircle;
import com.zfz.typhoon.model.vo.RiskAssessmentVO;
import com.zfz.typhoon.service.CityService;
import com.zfz.typhoon.service.RiskAssessmentService;
import com.zfz.typhoon.service.TyphoonService;
import com.zfz.typhoon.service.WindCircleService;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.DBUtil;
import com.zfz.typhoon.util.DateUtil;
import com.zfz.typhoon.util.GeoUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 城市风险预评估实现。
 *
 * <p><b>风险评分模型（0—100）</b>
 *
 * <p>由三个分量加权求和，每个分量都有明确的物理含义，便于答辩时解释：
 *
 * <table border="1">
 *   <caption>风险评分分量</caption>
 *   <tr><th>分量</th><th>满分</th><th>依据</th></tr>
 *   <tr><td>风的影响</td><td>60</td>
 *       <td>城市预计遭遇的风力等级（由风圈象限半径判定）/ 17 级 × 60</td></tr>
 *   <tr><td>距离衰减</td><td>25</td>
 *       <td>(1 − 距离 / 评估半径) × 25，越近越危险</td></tr>
 *   <tr><td>城市脆弱性</td><td>15</td>
 *       <td>城市表 {@code base_risk_level}（1—4）/ 4 × 15</td></tr>
 * </table>
 *
 * <p>等级划分：&lt;35 低 / &lt;60 中 / &lt;80 高 / ≥80 极高。
 *
 * <p>风力等级估计采用"先查风圈、后按距离衰减"的两段式：
 * <ol>
 *   <li>城市若落在某个等级的风圈内（用<b>该城市所属象限</b>的半径判定），
 *       则预计风力取该等级；12 级风圈内直接记 12 级以上；</li>
 *   <li>落在所有风圈之外时，按每远离 60 km 递减 1 级的经验速率衰减，
 *       下限 1 级。60 km 是台风外围气压梯度对应的经验量级。</li>
 * </ol>
 *
 * @author 追风者项目组
 */
public class RiskAssessmentServiceImpl implements RiskAssessmentService {

    /** 风的影响分量满分 */
    private static final double WEIGHT_WIND = 60D;
    /** 距离衰减分量满分 */
    private static final double WEIGHT_DISTANCE = 25D;
    /** 城市脆弱性分量满分 */
    private static final double WEIGHT_VULNERABILITY = 15D;

    /** 风力量化所参照的最大风力等级（超强台风上限） */
    private static final double MAX_WIND_FORCE = 17D;

    /** 城市风圈影响衰减速率：每远离 60 km 递减 1 级 */
    private static final double FALLOFF_KM_PER_LEVEL = 60D;

    /** 城市基础风险等级的量化上限 */
    private static final double MAX_BASE_RISK = 4D;

    /** 风圈等级（由高到低），用于"取城市落入的最高等级" */
    private static final int[] CIRCLE_LEVELS = {12, 10, 7};

    /** 等级分档阈值 */
    private static final double LEVEL_LOW_TO_MEDIUM = 35D;
    private static final double LEVEL_MEDIUM_TO_HIGH = 60D;
    private static final double LEVEL_HIGH_TO_EXTREME = 80D;

    /** 受影响时段：以预计到达时刻为中心的窗口（小时） */
    private static final double WINDOW_BEFORE_HOURS = 3D;
    private static final double WINDOW_AFTER_HOURS = 6D;

    /** 移速过小（近乎停滞）时，不计算 ETA，避免除零与无穷大时间 */
    private static final double MIN_MOVE_SPEED = 1D;

    private final RiskAssessmentDao riskDao;
    private final CityDao cityDao;
    private final SystemSettingDao settingDao;
    private final TyphoonService typhoonService;
    private final CityService cityService;
    private final WindCircleService windCircleService;

    /**
     * 生产用构造：按默认实现装配依赖。
     * <p>之所以把它拆成"无参 + 带参"两个构造，是为了给单元测试留出注入替身的缝隙：
     * 依赖全部在字段初始化里 new 出来的话，测试就必须连上真实 MySQL 才能跑，
     * 单元测试也就退化成了集成测试。
     */
    public RiskAssessmentServiceImpl() {
        this(new RiskAssessmentDaoImpl(), new CityDaoImpl(), new SystemSettingDaoImpl(), new TyphoonServiceImpl(), new CityServiceImpl(), new WindCircleServiceImpl());
    }

    /**
     * 测试用构造：注入替身依赖（Mockito 生成的 mock 或手写桩）。
     * <p>参数顺序与上方字段声明一致，均为本服务运行所需的协作者。
     */
    public RiskAssessmentServiceImpl(RiskAssessmentDao riskDao, CityDao cityDao, SystemSettingDao settingDao, TyphoonService typhoonService, CityService cityService, WindCircleService windCircleService) {
        this.riskDao = riskDao;
        this.cityDao = cityDao;
        this.settingDao = settingDao;
        this.typhoonService = typhoonService;
        this.cityService = cityService;
        this.windCircleService = windCircleService;
    }

    @Override
    public List<RiskAssessmentVO> listLatest(long typhoonId) {
        typhoonService.requireTyphoon(typhoonId);
        List<RiskAssessment> rows = riskDao.listLatestByTyphoon(typhoonId);
        List<RiskAssessmentVO> result = new ArrayList<RiskAssessmentVO>(rows.size());
        for (RiskAssessment ra : rows) {
            result.add(toVO(ra));
        }
        sortByScoreDesc(result);
        return result;
    }

    @Override
    public List<RiskAssessmentVO> assess(long typhoonId) {
        return assess(typhoonId, null);
    }

    @Override
    public List<RiskAssessmentVO> assess(long typhoonId, LocalDateTime obsTime) {
        typhoonService.requireTyphoon(typhoonId);

        TyphoonTrack basePoint = requireBasePoint(typhoonId, obsTime);
        double centerLat = basePoint.getLatitude().doubleValue();
        double centerLon = basePoint.getLongitude().doubleValue();
        // 中心位置与风圈必须取自同一时刻。若写成"最新风圈 + 历史中心"，
        // 重演出来的风圈半径会与中心位置差好几个时次，评分完全没有意义。
        WindCircle circle = windCircleService.at(typhoonId, obsTime);
        int typhoonForce = basePoint.getWindForce() == null ? 0 : basePoint.getWindForce().intValue();
        double moveSpeed = basePoint.getMoveSpeed() == null ? 0D : basePoint.getMoveSpeed().doubleValue();

        double rangeKm = settingDao.getDouble(KEY_RANGE_KM, DEFAULT_RANGE_KM);
        LocalDateTime baseTime = basePoint.getObsTime() == null ? DateUtil.now() : basePoint.getObsTime();
        LocalDateTime now = DateUtil.now();

        List<RiskAssessmentVO> result = new ArrayList<RiskAssessmentVO>();
        for (City city : cityService.asMap().values()) {
            if (city.getLatitude() == null || city.getLongitude() == null) {
                continue;
            }
            double distance = GeoUtil.distanceKm(centerLat, centerLon,
                    city.getLatitude().doubleValue(), city.getLongitude().doubleValue());
            if (distance > rangeKm) {
                continue;
            }
            result.add(evaluate(city, centerLat, centerLon, distance, circle,
                    typhoonForce, moveSpeed, baseTime, now));
        }

        sortByScoreDesc(result);
        return result;
    }

    /**
     * 取本次评估的基准实测点（中心位置来源）。
     *
     * <p>抽成独立方法是为了让 {@link #assessCity} 的"半径外兜底"也能拿到
     * 同一个基准时刻——否则前端下拉选中某城市后，兜底卡片上的基准时间会是空的。
     *
     * @param typhoonId 台风ID
     * @param obsTime   基准观测时刻，可为 null（取最新）
     * @return 基准实测点（经纬度齐全）
     * @throws BusinessException 无实测轨迹，或指定时刻之前没有任何轨迹时抛出（400）
     */
    private TyphoonTrack requireBasePoint(long typhoonId, LocalDateTime obsTime) {
        TyphoonTrack point = obsTime == null
                ? typhoonService.latestTrack(typhoonId)
                : typhoonService.trackAt(typhoonId, obsTime);
        if (point == null || point.getLatitude() == null || point.getLongitude() == null) {
            throw BusinessException.badRequest(obsTime == null
                    ? "该台风暂无实测轨迹，无法进行城市风险评估"
                    : "该台风在所选基准时刻之前没有实测轨迹，请另选时次");
        }
        return point;
    }

    @Override
    public List<RiskAssessmentVO> assessAndSave(long typhoonId, long userId) {
        return assessAndSave(typhoonId, userId, null);
    }

    @Override
    public List<RiskAssessmentVO> assessAndSave(long typhoonId, long userId, LocalDateTime obsTime) {
        List<RiskAssessmentVO> list = assess(typhoonId, obsTime);

        // 先把实体全部构造好（纯内存计算），再进事务写库，
        // 免得把读城市/读风圈这些只读步骤也拖进事务窗口。
        final List<RiskAssessment> entities = new ArrayList<RiskAssessment>(list.size());
        LocalDateTime now = DateUtil.now();
        for (RiskAssessmentVO vo : list) {
            RiskAssessment ra = new RiskAssessment();
            ra.setTyphoonId(Long.valueOf(typhoonId));
            ra.setCityId(vo.getCityId());
            ra.setUserId(Long.valueOf(userId));
            ra.setAssessmentTime(now);
            // 基准时刻取自 VO 回传值（而非入参），因为服务端可能做了容错回落，
            // 存档要记"实际用的那个基准"，否则复盘时对不上页面当时看到的结论。
            ra.setObsTime(DateUtil.parseLenient(vo.getBaseTime()));
            ra.setDistanceKm(vo.getDistance());
            ra.setEtaTime(DateUtil.parse(vo.getEta()));
            ra.setAffectedStartTime(DateUtil.parse(vo.getAffectedStart()));
            ra.setAffectedEndTime(DateUtil.parse(vo.getAffectedEnd()));
            ra.setRiskLevel(vo.getLevel());
            ra.setRiskScore(vo.getRiskScore());
            ra.setSuggestion(vo.getAdvice());
            ra.setCreatedAt(now);
            entities.add(ra);
        }

        // 「清旧 + 逐条写入」必须原子。否则中途失败会留下"一半是上一轮、
        // 一半是这一轮"的评估结果，而前端拿到的是最新一轮的 VO，
        // 两边对不上，排查时极难定位。
        DBUtil.runInTransaction(new DBUtil.TxWork<Void>() {
            @Override
            public Void run() {
                riskDao.deleteByTyphoon(typhoonId);
                for (RiskAssessment ra : entities) {
                    riskDao.insert(ra);
                }
                return null;
            }
        });
        return list;
    }

    @Override
    public RiskAssessmentVO assessCity(long typhoonId, long cityId) {
        return assessCity(typhoonId, cityId, null);
    }

    @Override
    public RiskAssessmentVO assessCity(long typhoonId, long cityId, LocalDateTime obsTime) {
        City city = cityService.getById(cityId);
        if (city == null) {
            throw BusinessException.notFound("城市不存在：id = " + cityId);
        }
        for (RiskAssessmentVO vo : assess(typhoonId, obsTime)) {
            if (vo.getCityId() != null && vo.getCityId().longValue() == cityId) {
                return vo;
            }
        }
        // 距离超出评估半径：仍返回该城市，但等级为低并说明原因，避免前端拿到空对象
        TyphoonTrack basePoint = requireBasePoint(typhoonId, obsTime);
        RiskAssessmentVO vo = new RiskAssessmentVO();
        vo.setCityId(city.getCityId());
        vo.setCityName(city.getCityName());
        vo.setProvince(city.getProvince());
        vo.setLevel("LOW");
        vo.setRiskLevel("低");
        vo.setRiskScore(Double.valueOf(0D));
        vo.setInWindCircle(Boolean.FALSE);
        vo.setBaseTime(DateUtil.format(basePoint.getObsTime()));
        vo.setAdvice("当前台风距离该城市较远，暂不构成风险，建议保持关注。");
        return vo;
    }

    @Override
    public int clear(long typhoonId) {
        return riskDao.deleteByTyphoon(typhoonId);
    }

    /* ------------------------------------------------------------------ */
    /*  核心：单城市评估                                                     */
    /* ------------------------------------------------------------------ */

    /**
     * 对单个城市做一次规则推算。
     *
     * @param city        城市
     * @param centerLat   台风中心纬度
     * @param centerLon   台风中心经度
     * @param distance    中心距城市距离（km）
     * @param circle      最新风圈；可为 null
     * @param typhoonForce 台风当前中心风力
     * @param moveSpeed   台风移速（km/h）
     * @param baseTime    推算基准时刻（默认取最后实测时刻，重演时取所选观测时次）
     * @param now         评估时刻
     * @return 评估视图对象
     */
    private RiskAssessmentVO evaluate(City city, double centerLat, double centerLon,
                                      double distance, WindCircle circle, int typhoonForce,
                                      double moveSpeed, LocalDateTime baseTime, LocalDateTime now) {
        String quadrant = GeoUtil.quadrant(centerLat, centerLon,
                city.getLatitude().doubleValue(), city.getLongitude().doubleValue());

        int expectedForce = estimateWindForce(distance, quadrant, circle, typhoonForce);
        boolean inCircle = expectedForce >= CIRCLE_LEVELS[CIRCLE_LEVELS.length - 1];

        double score = WEIGHT_WIND * Math.min(expectedForce / MAX_WIND_FORCE, 1D)
                + WEIGHT_DISTANCE * Math.max(1D - distance / rangeKmOrDefault(), 0D)
                + WEIGHT_VULNERABILITY * baseRiskRatio(city);
        score = GeoUtil.round(clamp(score, 0D, 100D), 2);

        String level = levelOf(score);

        RiskAssessmentVO vo = new RiskAssessmentVO();
        vo.setCityId(city.getCityId());
        vo.setCityName(city.getCityName());
        vo.setProvince(city.getProvince());
        vo.setDistance(Double.valueOf(GeoUtil.round(distance, 1)));
        vo.setWindLevel(expectedForce + " 级");
        vo.setLevel(level);
        vo.setRiskLevel(textOf(level));
        vo.setRiskScore(Double.valueOf(score));
        vo.setInWindCircle(Boolean.valueOf(inCircle));
        vo.setAdvice(adviceOf(level, city, expectedForce, inCircle));
        // 回传服务端实际使用的基准时刻：前端必须显示它，而不是自己拼一个"当前时间"。
        // 这正是本页早先"页面显示的时间与推算起点不一致"那处缺陷的根治点。
        vo.setBaseTime(DateUtil.format(baseTime));

        // ETA：以基准时刻为起点推算，避免"用当前时刻"导致每次刷新时间都在漂移
        if (moveSpeed > MIN_MOVE_SPEED) {
            double hours = distance / moveSpeed;
            LocalDateTime eta = DateUtil.plusHours(baseTime, hours);
            vo.setEta(DateUtil.format(eta));
            vo.setAffectedStart(DateUtil.format(DateUtil.plusHours(eta, -WINDOW_BEFORE_HOURS)));
            vo.setAffectedEnd(DateUtil.format(DateUtil.plusHours(eta, WINDOW_AFTER_HOURS)));
        } else {
            // 台风停滞：给不出 ETA，明确留空由前端显示"—"，而不是编一个时间
            vo.setEta(null);
            vo.setAffectedStart(DateUtil.format(now));
            vo.setAffectedEnd(null);
        }
        return vo;
    }

    /**
     * 估计城市将遭遇的风力等级。
     *
     * @param distance     中心距城市距离（km）
     * @param quadrant     城市相对台风中心的象限（ne/se/sw/nw）
     * @param circle       最新风圈；可为 null
     * @param typhoonForce 台风当前中心风力
     * @return 预计风力等级（1—17）
     */
    private int estimateWindForce(double distance, String quadrant, WindCircle circle, int typhoonForce) {
        if (circle != null) {
            for (int level : CIRCLE_LEVELS) {
                int radius = circle.radius(level, quadrant);
                if (radius > 0 && distance <= radius) {
                    // 落入 12 级风圈时，若中心风力更高则以中心风力为准
                    return level == CIRCLE_LEVELS[0] ? Math.max(level, typhoonForce) : level;
                }
            }
        }
        // 所有风圈之外：按经验速率衰减
        int fallback = typhoonForce > 0 ? typhoonForce : 7;
        int decayed = (int) Math.round(fallback - distance / FALLOFF_KM_PER_LEVEL);
        return (int) clamp(decayed, 1D, MAX_WIND_FORCE);
    }

    /**
     * 城市基础风险等级归一化到 0—1。
     *
     * @param city 城市
     * @return 归一化值；字段缺失时按中位 0.5
     */
    private double baseRiskRatio(City city) {
        if (city.getBaseRiskLevel() == null) {
            return 0.5D;
        }
        return clamp(city.getBaseRiskLevel().doubleValue() / MAX_BASE_RISK, 0D, 1D);
    }

    /**
     * 评分 → 英文等级代号。
     *
     * @param score 风险评分
     * @return LOW / MEDIUM / HIGH / EXTREME
     */
    private String levelOf(double score) {
        if (score < LEVEL_LOW_TO_MEDIUM) {
            return "LOW";
        }
        if (score < LEVEL_MEDIUM_TO_HIGH) {
            return "MEDIUM";
        }
        return score < LEVEL_HIGH_TO_EXTREME ? "HIGH" : "EXTREME";
    }

    /**
     * 英文等级代号 → 中文。
     *
     * @param level 等级代号
     * @return 低 / 中 / 高 / 极高
     */
    private String textOf(String level) {
        if ("LOW".equals(level)) {
            return "低";
        }
        if ("MEDIUM".equals(level)) {
            return "中";
        }
        if ("HIGH".equals(level)) {
            return "高";
        }
        return "极高";
    }

    /**
     * 按等级生成防范建议。
     *
     * @param level        等级代号
     * @param city         城市
     * @param expectedForce 预计风力
     * @param inCircle     是否处于风圈内
     * @return 建议文案
     */
    private String adviceOf(String level, City city, int expectedForce, boolean inCircle) {
        String where = city.getCityName();
        if ("EXTREME".equals(level)) {
            return where + "预计遭遇 " + expectedForce + " 级大风，风险极高："
                    + "请立即停止户外作业与集会，危险区域人员按官方指令转移，"
                    + "沿海船只全部回港避风，切勿外出。";
        }
        if ("HIGH".equals(level)) {
            return where + "将进入强风影响范围，请加固门窗与临时构筑物，"
                    + "清理阳台悬挂物，储备至少 3 天的饮用水与食品，"
                    + "密切关注官方预警并做好随时转移的准备。";
        }
        if ("MEDIUM".equals(level)) {
            return where + "将出现较明显风雨，请检查排水口是否通畅，"
                    + (inCircle ? "避免前往海边与低洼路段，"
                    : "减少不必要的外出，")
                    + "留意官方最新预报。";
        }
        return where + "受外围环流影响有限，建议保持关注，暂无需特别措施。";
    }

    /**
     * 取评估半径（带默认值兜底，供评分公式使用）。
     *
     * @return 评估半径（km）
     */
    private double rangeKmOrDefault() {
        double v = settingDao.getDouble(KEY_RANGE_KM, DEFAULT_RANGE_KM);
        return v <= 0D ? DEFAULT_RANGE_KM : v;
    }

    /**
     * 实体 → 视图对象（联表带出城市信息）。
     *
     * @param ra 评估实体
     * @return 视图对象
     */
    private RiskAssessmentVO toVO(RiskAssessment ra) {
        City city = ra.getCityId() == null ? null : cityDao.findById(ra.getCityId().longValue());
        return RiskAssessmentVO.of(ra,
                city == null ? null : city.getCityName(),
                city == null ? null : city.getProvince());
    }

    /**
     * 按风险评分降序排列。
     *
     * @param list 待排序列表
     */
    private void sortByScoreDesc(List<RiskAssessmentVO> list) {
        Collections.sort(list, new Comparator<RiskAssessmentVO>() {
            @Override
            public int compare(RiskAssessmentVO a, RiskAssessmentVO b) {
                double sa = a.getRiskScore() == null ? 0D : a.getRiskScore().doubleValue();
                double sb = b.getRiskScore() == null ? 0D : b.getRiskScore().doubleValue();
                return Double.compare(sb, sa);
            }
        });
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
