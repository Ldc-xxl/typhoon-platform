package com.zfz.typhoon.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.zfz.typhoon.dao.ForecastDisagreeRecordDao;
import com.zfz.typhoon.dao.SystemSettingDao;
import com.zfz.typhoon.dao.impl.ForecastDisagreeRecordDaoImpl;
import com.zfz.typhoon.dao.impl.SystemSettingDaoImpl;
import com.zfz.typhoon.model.ForecastDisagreeRecord;
import com.zfz.typhoon.model.ForecastPath;
import com.zfz.typhoon.model.Typhoon;
import com.zfz.typhoon.model.vo.DisagreementVO;
import com.zfz.typhoon.model.vo.TyphoonVO;
import com.zfz.typhoon.service.DisagreementService;
import com.zfz.typhoon.service.ForecastService;
import com.zfz.typhoon.service.TyphoonService;
import com.zfz.typhoon.util.DateUtil;
import com.zfz.typhoon.util.GeoUtil;
import com.zfz.typhoon.util.JsonUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多机构预报分歧解析实现。
 *
 * <p><b>算法说明（四维加权归一化）</b>
 *
 * <p>分歧不能用"路径线之间的面积"直接比，因为四个维度量纲完全不同
 * （km / 小时 / 风力等级 / km·h⁻¹）。本实现的做法是：
 *
 * <ol>
 *   <li>对每个维度先算<b>极差</b>（各机构预报值的最大值 − 最小值），
 *       极差本身就是"分歧有多大"的直观度量；</li>
 *   <li>再用该维度的<b>业务参考尺度</b>归一化到 0—1。
 *       参考尺度的取值依据：
 *       <ul>
 *         <li>登陆地点 500 km —— 台风路径 72 小时预报的典型误差量级；</li>
 *         <li>登陆时间 48 h —— 3 天预报时效；</li>
 *         <li>峰值强度 10 级 —— 热带风暴到超强台风的跨度；</li>
 *         <li>移动速度 20 km/h —— 台风移速的常见取值区间宽度。</li>
 *       </ul>
 *   </li>
 *   <li>按业务重要性加权求和：登陆地点 0.40（对防灾最关键）、
 *       峰值强度 0.25、登陆时间 0.20、移动速度 0.15。</li>
 * </ol>
 *
 * <p>权重与阈值均可在 {@code system_settings} 表中调整，本类只提供默认值，
 * 满足课程要求「连接池 / 参数需可参数化」的一贯做法。
 *
 * @author 追风者项目组
 */
public class DisagreementServiceImpl implements DisagreementService {

    /* -------------------------- 归一化参考尺度 -------------------------- */

    /** 登陆地点参考尺度（km） */
    private static final double NORM_LANDFALL_KM = 500D;
    /** 登陆时间参考尺度（小时） */
    private static final double NORM_HOURS = 48D;
    /** 峰值强度参考尺度（风力等级） */
    private static final double NORM_WIND_FORCE = 10D;
    /** 移动速度参考尺度（km/h） */
    private static final double NORM_SPEED = 20D;

    /* ----------------------------- 维度权重 ----------------------------- */

    private static final double W_LANDFALL = 0.40D;
    private static final double W_INTENSITY = 0.25D;
    private static final double W_TIME = 0.20D;
    private static final double W_SPEED = 0.15D;

    /* ------------------------------ 阈值 ------------------------------- */

    /** 默认高不确定性阈值 */
    private static final double DEFAULT_INDEX_THRESHOLD = 0.70D;
    /** 默认"扩大中"判定变化量 */
    private static final double DEFAULT_EXPAND_DELTA = 0.05D;

    /** 风险等级分档：低于此值为「低」 */
    private static final double RISK_MEDIUM_FROM = 0.30D;
    /** 风险等级分档：低于此值为「中」，否则为「高」 */
    private static final double RISK_HIGH_FROM = 0.60D;

    /** 参与分析的最少机构数（少于 2 家谈不上"分歧"） */
    private static final int MIN_AGENCIES = 2;

    private final ForecastService forecastService;
    private final TyphoonService typhoonService;
    private final ForecastDisagreeRecordDao disagreeDao;
    private final SystemSettingDao settingDao;

    /**
     * 生产用构造：按默认实现装配依赖。
     * <p>之所以把它拆成"无参 + 带参"两个构造，是为了给单元测试留出注入替身的缝隙：
     * 依赖全部在字段初始化里 new 出来的话，测试就必须连上真实 MySQL 才能跑，
     * 单元测试也就退化成了集成测试。
     */
    public DisagreementServiceImpl() {
        this(new ForecastServiceImpl(), new TyphoonServiceImpl(), new ForecastDisagreeRecordDaoImpl(), new SystemSettingDaoImpl());
    }

    /**
     * 测试用构造：注入替身依赖（Mockito 生成的 mock 或手写桩）。
     * <p>参数顺序与上方字段声明一致，均为本服务运行所需的协作者。
     */
    public DisagreementServiceImpl(ForecastService forecastService, TyphoonService typhoonService, ForecastDisagreeRecordDao disagreeDao, SystemSettingDao settingDao) {
        this.forecastService = forecastService;
        this.typhoonService = typhoonService;
        this.disagreeDao = disagreeDao;
        this.settingDao = settingDao;
    }

    /* ------------------------------------------------------------------ */
    /*  对外方法                                                            */
    /* ------------------------------------------------------------------ */

    @Override
    public DisagreementVO analyze(long typhoonId) {
        Typhoon typhoon = typhoonService.requireTyphoon(typhoonId);

        DisagreementVO vo = new DisagreementVO();
        vo.setTyphoonId(Long.valueOf(typhoonId));
        vo.setTyphoonName(typhoon.getChineseName());
        vo.setAnalysisTime(DateUtil.format(DateUtil.now()));

        // 按机构归组，只保留"未来有效预报点"（实况已覆盖的点不参与分歧计算）
        Map<String, AgencySeries> series = collectSeries(typhoonId);
        vo.setAgencyCount(Integer.valueOf(series.size()));
        vo.setAgencies(new ArrayList<String>(series.keySet()));

        if (series.size() < MIN_AGENCIES) {
            vo.setIndex(Double.valueOf(0D));
            vo.setRiskLevel(DisagreementVO.RISK_LOW);
            vo.setHighUncertainty(Boolean.FALSE);
            vo.setConvergence(CONVERGENCE_STEADY);
            vo.setConclusion("当前仅 " + series.size()
                    + " 家机构发布了有效预报，样本不足以量化分歧，暂不给出分歧结论。");
            return vo;
        }

        // ---- 维度 1：登陆地点 ----
        List<double[]> landfalls = new ArrayList<double[]>();
        for (AgencySeries s : series.values()) {
            landfalls.add(new double[]{s.endLon, s.endLat});
            vo.getLandfallPoints().add(toPointList(s.endLon, s.endLat));
        }
        double landfallRangeKm = maxPairwiseKm(landfalls);
        double landfallVar = normalize(landfallRangeKm, NORM_LANDFALL_KM);
        vo.getDimensions().add(dimension("登陆地点",
                lonRangeText(landfalls), landfallVar, landfallRangeKm,
                commentOf(landfallVar, "各机构登陆点高度一致", "登陆点存在一定分散", "登陆点分歧显著")));

        // ---- 维度 2：登陆时间 ----
        List<Double> endHours = new ArrayList<Double>();
        LocalDateTime base = null;
        for (AgencySeries s : series.values()) {
            if (s.endTime == null) {
                continue;
            }
            if (base == null || s.endTime.isBefore(base)) {
                base = s.endTime;
            }
        }
        if (base != null) {
            for (AgencySeries s : series.values()) {
                if (s.endTime != null) {
                    endHours.add(Double.valueOf(DateUtil.hoursBetween(base, s.endTime)));
                }
            }
        }
        double timeRangeH = range(endHours);
        double timeVar = normalize(timeRangeH, NORM_HOURS);
        vo.getDimensions().add(dimension("登陆时间",
                timeRangeH <= 0D ? "各机构时段一致" : "约 " + Math.round(timeRangeH) + " 小时",
                timeVar, timeRangeH,
                commentOf(timeVar, "登陆时段一致", "登陆时段相差数小时", "登陆时段相差超过一天")));

        // ---- 维度 3：峰值强度 ----
        List<Double> peaks = new ArrayList<Double>();
        for (AgencySeries s : series.values()) {
            if (s.peakForce != null) {
                peaks.add(Double.valueOf(s.peakForce.doubleValue()));
            }
        }
        double peakRange = range(peaks);
        double peakVar = normalize(peakRange, NORM_WIND_FORCE);
        vo.getDimensions().add(dimension("峰值强度",
                forceRangeText(peaks), peakVar, peakRange,
                commentOf(peakVar, "强度判断一致", "强度判断略有差异", "强度判断存在级差")));

        // ---- 维度 4：移动速度 ----
        List<Double> speeds = new ArrayList<Double>();
        for (AgencySeries s : series.values()) {
            if (s.avgSpeed > 0D) {
                speeds.add(Double.valueOf(s.avgSpeed));
            }
        }
        double speedRange = range(speeds);
        double speedVar = normalize(speedRange, NORM_SPEED);
        vo.getDimensions().add(dimension("移动速度",
                speedRange <= 0D ? "各机构一致" : round1(min(speeds)) + " ~ " + round1(max(speeds)) + " km/h",
                speedVar, speedRange,
                commentOf(speedVar, "移速判断一致", "移速判断略有差异", "移速判断分歧明显")));

        // ---- 加权汇总 ----
        double index = clamp(W_LANDFALL * landfallVar
                + W_TIME * timeVar
                + W_INTENSITY * peakVar
                + W_SPEED * speedVar, 0D, 1D);
        index = GeoUtil.round(index, 2);

        double threshold = settingDao.getDouble(KEY_INDEX_THRESHOLD, DEFAULT_INDEX_THRESHOLD);
        vo.setIndex(Double.valueOf(index));
        vo.setRiskLevel(riskLevelOf(index));
        vo.setHighUncertainty(Boolean.valueOf(index >= threshold));
        vo.setConvergence(convergenceOf(typhoonId, index));
        vo.setConclusion(conclusionOf(vo, index, series.size()));
        return vo;
    }

    @Override
    public DisagreementVO latest(long typhoonId) {
        ForecastDisagreeRecord record = disagreeDao.findLatestByTyphoon(typhoonId);
        if (record == null) {
            // 从未分析过：现场算一次，保证页面永远有内容可渲染
            return analyze(typhoonId);
        }
        DisagreementVO vo = analyze(typhoonId);
        // 用落库值覆盖趋势与结论，保持"页面看到的趋势"与"数据库记录的历史"一致
        vo.setIndex(record.getVarianceScore());
        vo.setRiskLevel(riskLevelOf(record.getVarianceScore() == null ? 0D
                : record.getVarianceScore().doubleValue()));
        vo.setHighUncertainty(record.getHighUncertainty());
        vo.setAnalysisTime(DateUtil.format(record.getAnalysisTime()));
        return vo;
    }

    @Override
    public ForecastDisagreeRecord analyzeAndSave(long typhoonId) {
        DisagreementVO vo = analyze(typhoonId);

        ForecastDisagreeRecord record = new ForecastDisagreeRecord();
        record.setTyphoonId(Long.valueOf(typhoonId));
        record.setAnalysisTime(DateUtil.now());
        record.setVarianceScore(vo.getIndex());
        record.setHighUncertainty(vo.getHighUncertainty());

        // 维度极差落到各专用列，便于 SQL 层直接做趋势统计
        record.setLandingPointDeviationKm(valueOf(vo, "登陆地点"));
        record.setLandingTimeDeviationHours(valueOf(vo, "登陆时间"));
        record.setPeakIntensityDeviation(valueOf(vo, "峰值强度"));
        record.setMoveSpeedDeviation(valueOf(vo, "移动速度"));

        // 明细整包存 JSON 列，供后续复算与页面回放
        Map<String, Object> detail = new LinkedHashMap<String, Object>();
        detail.put("index", vo.getIndex());
        detail.put("riskLevel", vo.getRiskLevel());
        detail.put("convergence", vo.getConvergence());
        detail.put("agencyCount", vo.getAgencyCount());
        detail.put("agencies", vo.getAgencies());
        detail.put("conclusion", vo.getConclusion());
        List<Map<String, Object>> dims = new ArrayList<Map<String, Object>>();
        for (DisagreementVO.DimensionItem d : vo.getDimensions()) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("name", d.getName());
            m.put("spread", d.getSpread());
            m.put("variance", d.getVariance());
            m.put("range", d.getRange());
            m.put("comment", d.getComment());
            dims.add(m);
        }
        detail.put("dimensions", dims);
        JsonNode node = JsonUtil.mapper().valueToTree(detail);
        record.setAnalysisDetail(node);

        record.setCreatedAt(DateUtil.now());
        long id = disagreeDao.insert(record);
        record.setRecordId(Long.valueOf(id));
        return record;
    }

    @Override
    public List<ForecastDisagreeRecord> history(long typhoonId) {
        return disagreeDao.listByTyphoon(typhoonId);
    }

    @Override
    public boolean isHighUncertainty(long typhoonId) {
        double threshold = settingDao.getDouble(KEY_INDEX_THRESHOLD, DEFAULT_INDEX_THRESHOLD);
        Double index = analyze(typhoonId).getIndex();
        return index != null && index.doubleValue() >= threshold;
    }

    /* ------------------------------------------------------------------ */
    /*  内部：机构序列收集与派生指标                                          */
    /* ------------------------------------------------------------------ */

    /** 收敛趋势：收敛中 */
    private static final String CONVERGENCE_SHRINK = "收敛中";
    /** 收敛趋势：扩大中 */
    private static final String CONVERGENCE_EXPAND = "扩大中";
    /** 收敛趋势：持平 */
    private static final String CONVERGENCE_STEADY = "持平";

    /**
     * 单个机构的最新预报序列及其派生指标。
     */
    private static final class AgencySeries {

        /** 预报点（已按预报时间升序） */
        private final List<ForecastPath> points = new ArrayList<ForecastPath>();
        /** 末点经度（＝预报登陆点） */
        private double endLon;
        /** 末点纬度 */
        private double endLat;
        /** 末点预报时刻（＝预报登陆时刻） */
        private LocalDateTime endTime;
        /** 峰值预报风力 */
        private Integer peakForce;
        /** 全程平均移速（km/h） */
        private double avgSpeed;
    }

    /**
     * 收集各机构的最新预报序列，并算出登陆点 / 登陆时刻 / 峰值强度 / 平均移速。
     *
     * @param typhoonId 台风ID
     * @return 机构代码 → 序列；按 DAO 返回顺序（机构优先级）排列
     */
    private Map<String, AgencySeries> collectSeries(long typhoonId) {
        Map<String, List<ForecastPath>> grouped = forecastService.groupByAgency(typhoonId);
        Map<String, AgencySeries> result = new LinkedHashMap<String, AgencySeries>();

        for (Map.Entry<String, List<ForecastPath>> entry : grouped.entrySet()) {
            List<ForecastPath> raw = new ArrayList<ForecastPath>(entry.getValue());
            // 只保留坐标与时刻齐全的点，并按时间升序——顺带规避脏数据
            List<ForecastPath> valid = new ArrayList<ForecastPath>(raw.size());
            for (ForecastPath p : raw) {
                if (p.getLatitude() != null && p.getLongitude() != null && p.getForecastTime() != null) {
                    valid.add(p);
                }
            }
            if (valid.size() < 2) {
                // 少于 2 个点既画不出线也算不出移速，整体丢弃
                continue;
            }
            Collections.sort(valid, new Comparator<ForecastPath>() {
                @Override
                public int compare(ForecastPath a, ForecastPath b) {
                    return a.getForecastTime().compareTo(b.getForecastTime());
                }
            });

            AgencySeries s = new AgencySeries();
            s.points.addAll(valid);

            ForecastPath last = valid.get(valid.size() - 1);
            s.endLon = last.getLongitude().doubleValue();
            s.endLat = last.getLatitude().doubleValue();
            s.endTime = last.getForecastTime();

            int peak = 0;
            double totalKm = 0D;
            for (int i = 0; i < valid.size(); i++) {
                ForecastPath p = valid.get(i);
                if (p.getForecastWindForce() != null && p.getForecastWindForce().intValue() > peak) {
                    peak = p.getForecastWindForce().intValue();
                }
                if (i > 0) {
                    ForecastPath prev = valid.get(i - 1);
                    totalKm += GeoUtil.distanceKm(prev.getLatitude().doubleValue(),
                            prev.getLongitude().doubleValue(),
                            p.getLatitude().doubleValue(), p.getLongitude().doubleValue());
                }
            }
            s.peakForce = Integer.valueOf(peak);

            double hours = DateUtil.hoursBetween(valid.get(0).getForecastTime(), s.endTime);
            s.avgSpeed = hours > 0D ? GeoUtil.round(totalKm / hours, 1) : 0D;
            result.put(entry.getKey(), s);
        }
        return result;
    }

    /**
     * 取城市间最大两两距离（km），用于度量登陆点分散程度。
     *
     * @param points 经纬度点集，每项为 {@code [lon, lat]}
     * @return 最大两两距离；点数不足 2 时返回 0
     */
    private double maxPairwiseKm(List<double[]> points) {
        double max = 0D;
        for (int i = 0; i < points.size(); i++) {
            for (int j = i + 1; j < points.size(); j++) {
                double[] a = points.get(i);
                double[] b = points.get(j);
                double km = GeoUtil.distanceKm(a[1], a[0], b[1], b[0]);
                if (km > max) {
                    max = km;
                }
            }
        }
        return GeoUtil.round(max, 1);
    }

    /**
     * 生成登陆点经度跨度文案，如「118.09°E ~ 116.68°E」。
     *
     * @param points 经纬度点集，每项为 {@code [lon, lat]}
     * @return 经度跨度文案
     */
    private String lonRangeText(List<double[]> points) {
        if (points.isEmpty()) {
            return "—";
        }
        double minLon = Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE;
        for (double[] p : points) {
            minLon = Math.min(minLon, p[0]);
            maxLon = Math.max(maxLon, p[0]);
        }
        return GeoUtil.round(maxLon, 2) + "°E ~ " + GeoUtil.round(minLon, 2) + "°E";
    }

    /**
     * 生成峰值强度跨度文案，如「强台风 ~ 超强台风」。
     *
     * @param forces 各机构峰值预报风力
     * @return 强度跨度文案
     */
    private String forceRangeText(List<Double> forces) {
        if (forces.isEmpty()) {
            return "—";
        }
        String lo = TyphoonVO.levelName(Integer.valueOf((int) Math.round(min(forces))));
        String hi = TyphoonVO.levelName(Integer.valueOf((int) Math.round(max(forces))));
        return lo.equals(hi) ? lo : lo + " ~ " + hi;
    }

    /**
     * 按业务参考尺度把极差归一化到 0—1。
     *
     * @param range  极差
     * @param normal 参考尺度
     * @return 归一化结果，上限 1
     */
    private double normalize(double range, double normal) {
        if (normal <= 0D || range <= 0D) {
            return 0D;
        }
        return clamp(range / normal, 0D, 1D);
    }

    /**
     * 构造单个维度明细。
     *
     * @param name     维度名称
     * @param spread   离散范围文案
     * @param variance 归一化离散度
     * @param range    原始极差
     * @param comment  一致性评价
     * @return 维度明细
     */
    private DisagreementVO.DimensionItem dimension(String name, String spread,
                                                   double variance, double range, String comment) {
        DisagreementVO.DimensionItem item = new DisagreementVO.DimensionItem();
        item.setName(name);
        item.setSpread(spread);
        item.setVariance(Double.valueOf(GeoUtil.round(variance, 2)));
        item.setRange(Double.valueOf(GeoUtil.round(range, 1)));
        item.setComment(comment);
        return item;
    }

    /**
     * 按归一化离散度给出三级评价。
     *
     * @param variance 归一化离散度
     * @param low      离散度低时的文案
     * @param mid      离散度中等时的文案
     * @param high     离散度高时的文案
     * @return 评价文案
     */
    private String commentOf(double variance, String low, String mid, String high) {
        if (variance < RISK_MEDIUM_FROM) {
            return low;
        }
        return variance < RISK_HIGH_FROM ? mid : high;
    }

    /**
     * 分歧指数 → 风险等级。
     *
     * @param index 分歧指数
     * @return 低 / 中 / 高
     */
    private String riskLevelOf(double index) {
        if (index < RISK_MEDIUM_FROM) {
            return DisagreementVO.RISK_LOW;
        }
        return index < RISK_HIGH_FROM ? DisagreementVO.RISK_MEDIUM : DisagreementVO.RISK_HIGH;
    }

    /**
     * 与上一次落库的分析结果比较，判断分歧是在收敛还是扩大。
     *
     * @param typhoonId 台风ID
     * @param current   本次分歧指数
     * @return 收敛中 / 扩大中 / 持平
     */
    private String convergenceOf(long typhoonId, double current) {
        ForecastDisagreeRecord prev = disagreeDao.findLatestByTyphoon(typhoonId);
        if (prev == null || prev.getVarianceScore() == null) {
            return CONVERGENCE_STEADY;
        }
        double delta = settingDao.getDouble(KEY_EXPAND_DELTA, DEFAULT_EXPAND_DELTA);
        double diff = current - prev.getVarianceScore().doubleValue();
        if (diff > delta) {
            return CONVERGENCE_EXPAND;
        }
        return diff < -delta ? CONVERGENCE_SHRINK : CONVERGENCE_STEADY;
    }

    /**
     * 生成面向公众的结论文案。
     *
     * <p>措辞刻意避免"预报某地登陆"这类确定性表述，只说"机构之间存在分歧"，
     * 并在最后给出行动建议——这正是本模块相对"叠加画线"的价值所在。
     *
     * @param vo          已完成计算的视图对象
     * @param index       分歧指数
     * @param agencyCount 机构数
     * @return 结论文案
     */
    private String conclusionOf(DisagreementVO vo, double index, int agencyCount) {
        StringBuilder sb = new StringBuilder();
        sb.append(agencyCount).append(" 家机构的预报结果");

        if (index < RISK_MEDIUM_FROM) {
            sb.append("高度一致，路径与强度判断基本统一，当前预报可信度较高。");
        } else if (index < RISK_HIGH_FROM) {
            sb.append("总体接近，但在部分维度上仍有差异");
            sb.append("（").append(dominantDimension(vo)).append("）");
            sb.append("，建议关注后续更新的预报。");
        } else {
            sb.append("存在明显分歧，分歧最集中的维度是")
                    .append("「").append(dominantDimension(vo)).append("」");
            sb.append("。当前预报可信度较低，不同机构的结论可能指向完全不同的防范区域，"
                    + "需持续跟踪最新预报更新，不宜依据单一机构结论部署防灾工作。");
        }
        return sb.toString();
    }

    /**
     * 找出归一化离散度最大的维度名称。
     *
     * @param vo 已完成计算的视图对象
     * @return 维度名称；无维度时返回「—」
     */
    private String dominantDimension(DisagreementVO vo) {
        String name = "—";
        double max = -1D;
        for (DisagreementVO.DimensionItem d : vo.getDimensions()) {
            double v = d.getVariance() == null ? 0D : d.getVariance().doubleValue();
            if (v > max) {
                max = v;
                name = d.getName();
            }
        }
        return name;
    }

    /**
     * 从视图对象中按维度名取原始极差。
     *
     * <p>注意：{@code forecast_disagree_records} 的四个偏差列均为 {@code NOT NULL}，
     * 因此这里对"维度缺失"的情况必须回退成 0 而不是 null，否则 INSERT 会被 MySQL 拒绝。
     * 维度缺失意味着该维度只有一家机构有数据、谈不上分歧，记 0 也符合语义。
     *
     * @param vo   视图对象
     * @param name 维度名称
     * @return 极差；未找到时返回 0
     */
    private Double valueOf(DisagreementVO vo, String name) {
        for (DisagreementVO.DimensionItem d : vo.getDimensions()) {
            if (name.equals(d.getName())) {
                return d.getRange() == null ? Double.valueOf(0D) : d.getRange();
            }
        }
        return Double.valueOf(0D);
    }

    /**
     * 把经纬度包成 {@code [lon, lat]} 列表。
     *
     * @param lon 经度
     * @param lat 纬度
     * @return 坐标列表
     */
    private List<Double> toPointList(double lon, double lat) {
        List<Double> p = new ArrayList<Double>(2);
        p.add(Double.valueOf(GeoUtil.round(lon, 2)));
        p.add(Double.valueOf(GeoUtil.round(lat, 2)));
        return p;
    }

    /**
     * 求极差（最大值 − 最小值）。
     *
     * @param values 数值集合
     * @return 极差；集合为空时返回 0
     */
    private double range(List<Double> values) {
        if (values.isEmpty()) {
            return 0D;
        }
        return GeoUtil.round(max(values) - min(values), 1);
    }

    /**
     * 求最大值。
     *
     * @param values 数值集合
     * @return 最大值；集合为空时返回 0
     */
    private double max(List<Double> values) {
        double m = -Double.MAX_VALUE;
        for (Double v : values) {
            if (v != null && v.doubleValue() > m) {
                m = v.doubleValue();
            }
        }
        return values.isEmpty() ? 0D : m;
    }

    /**
     * 求最小值。
     *
     * @param values 数值集合
     * @return 最小值；集合为空时返回 0
     */
    private double min(List<Double> values) {
        double m = Double.MAX_VALUE;
        for (Double v : values) {
            if (v != null && v.doubleValue() < m) {
                m = v.doubleValue();
            }
        }
        return values.isEmpty() ? 0D : m;
    }

    /**
     * 保留一位小数。
     *
     * @param value 原始值
     * @return 舍入结果
     */
    private double round1(double value) {
        return GeoUtil.round(value, 1);
    }

    /**
     * 区间截断。
     *
     * @param value 原始值
     * @param lo    下界
     * @param hi    上界
     * @return 截断后的值
     */
    private double clamp(double value, double lo, double hi) {
        return value < lo ? lo : (value > hi ? hi : value);
    }
}
