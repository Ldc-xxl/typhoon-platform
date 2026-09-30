package com.zfz.typhoon.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.zfz.typhoon.dao.AiPredictionRecordDao;
import com.zfz.typhoon.dao.SystemSettingDao;
import com.zfz.typhoon.dao.impl.AiPredictionRecordDaoImpl;
import com.zfz.typhoon.dao.impl.SystemSettingDaoImpl;
import com.zfz.typhoon.model.AiPredictionRecord;
import com.zfz.typhoon.model.City;
import com.zfz.typhoon.model.ForecastPath;
import com.zfz.typhoon.model.Typhoon;
import com.zfz.typhoon.model.vo.AiPredictionVO;
import com.zfz.typhoon.service.AiPredictionService;
import com.zfz.typhoon.service.CityService;
import com.zfz.typhoon.service.ForecastService;
import com.zfz.typhoon.service.TyphoonService;
import com.zfz.typhoon.util.DateUtil;
import com.zfz.typhoon.util.GeoUtil;
import com.zfz.typhoon.util.JsonUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * AI 路径智能测算实现（规则化多机构融合）。
 *
 * <p>算法说明见 {@link AiPredictionService} 的类注释。这里补充三处实现细节：
 *
 * <ul>
 *   <li><b>时间对齐</b>用线性插值。某机构在两个已知预报时刻之间取值时按时间比例插值，
 *       超出其预报范围则不参与该时刻的融合（而不是拿端点硬撑），
 *       避免把"没预报"当成"预报了和端点一样"。</li>
 *   <li><b>置信度</b>＝100 − 归一化离散度 × 100。离散度取各时刻机构坐标两两距离
 *       平均值的最大值，代表"最不确定的那一段"。取最大值而非平均值，
 *       是因为防灾关心的是最坏情况。</li>
 *   <li><b>降级</b>：不足 2 家机构时无法算离散度，置信度按 50 给出并把
 *       状态标为 {@code PENDING}，文案里如实说明"样本不足"。</li>
 * </ul>
 *
 * @author 追风者项目组
 */
public class AiPredictionServiceImpl implements AiPredictionService {

    /** 融合结果对应的"虚拟模型"名称 */
    private static final String FUSION_MODEL = "多模型融合";

    /** 触发测算时前端可能不传模型名，用这个兜底（与 data.js 的模型清单一致） */
    private static final String DEFAULT_MODEL = "DeepSeek-V3";

    /** 置信度上限，避免出现"100% 把握"这种不诚实的表述 */
    private static final double MAX_CONFIDENCE = 95D;

    /** 参与融合的最少机构数 */
    private static final int MIN_AGENCIES = 2;

    /** 记录状态 */
    private static final String STATUS_VALIDATED = "VALIDATED";
    private static final String STATUS_PENDING = "PENDING";

    /** 强度趋势文案所用的风力等级描述阈值 */
    private static final int STRONG_FORCE = 12;

    private final AiPredictionRecordDao aiDao;
    private final SystemSettingDao settingDao;
    private final TyphoonService typhoonService;
    private final ForecastService forecastService;
    private final CityService cityService;

    /**
     * 生产用构造：按默认实现装配依赖。
     * <p>之所以把它拆成"无参 + 带参"两个构造，是为了给单元测试留出注入替身的缝隙：
     * 依赖全部在字段初始化里 new 出来的话，测试就必须连上真实 MySQL 才能跑，
     * 单元测试也就退化成了集成测试。
     */
    public AiPredictionServiceImpl() {
        this(new AiPredictionRecordDaoImpl(), new SystemSettingDaoImpl(), new TyphoonServiceImpl(), new ForecastServiceImpl(), new CityServiceImpl());
    }

    /**
     * 测试用构造：注入替身依赖（Mockito 生成的 mock 或手写桩）。
     * <p>参数顺序与上方字段声明一致，均为本服务运行所需的协作者。
     */
    public AiPredictionServiceImpl(AiPredictionRecordDao aiDao, SystemSettingDao settingDao, TyphoonService typhoonService, ForecastService forecastService, CityService cityService) {
        this.aiDao = aiDao;
        this.settingDao = settingDao;
        this.typhoonService = typhoonService;
        this.forecastService = forecastService;
        this.cityService = cityService;
    }

    @Override
    public AiPredictionVO latest(long typhoonId) {
        Typhoon typhoon = typhoonService.requireTyphoon(typhoonId);
        return build(typhoon, null);
    }

    @Override
    public List<AiPredictionVO.ModelItem> listModels(long typhoonId) {
        List<AiPredictionRecord> rows = aiDao.listLatestPerModel(typhoonId);
        List<AiPredictionVO.ModelItem> result = new ArrayList<AiPredictionVO.ModelItem>(rows.size());
        for (AiPredictionRecord r : rows) {
            result.add(AiPredictionVO.ModelItem.of(r));
        }
        return result;
    }

    @Override
    public AiPredictionVO trigger(long typhoonId, String modelName) {
        Typhoon typhoon = typhoonService.requireTyphoon(typhoonId);
        String model = modelName == null || modelName.trim().isEmpty()
                ? DEFAULT_MODEL : modelName.trim();
        return build(typhoon, model);
    }

    @Override
    public int clear(long typhoonId) {
        return aiDao.deleteByTyphoon(typhoonId);
    }

    /* ------------------------------------------------------------------ */
    /*  核心：融合测算                                                       */
    /* ------------------------------------------------------------------ */

    /**
     * 执行一次融合测算；若指定了模型名则同时落库。
     *
     * @param typhoon   台风实体
     * @param saveModel 需要落库的模型名；为 null 表示只算不存
     * @return 测算结果
     */
    private AiPredictionVO build(Typhoon typhoon, String saveModel) {
        long typhoonId = typhoon.getTyphoonId().longValue();

        Map<String, List<ForecastPath>> grouped = forecastService.groupByAgency(typhoonId);
        // 只保留点足够、且时刻可用的机构
        Map<String, List<ForecastPath>> usable = normalize(grouped);

        AiPredictionVO vo = new AiPredictionVO();
        vo.setTyphoonId(Long.valueOf(typhoonId));
        vo.setVersion(DEFAULT_PROMPT_VERSION);
        vo.setColor("#2BB8C4");
        vo.setDash("6 5");
        vo.setPredictionTime(DateUtil.format(DateUtil.now()));
        vo.setDisclaimer(AiPredictionVO.DISCLAIMER);

        if (usable.size() < MIN_AGENCIES) {
            // 样本不足：给不出融合路径，如实说明而不是编一条
            vo.setConsistency(Integer.valueOf(50));
            vo.setConsistencyText("样本不足");
            vo.setConfidence(0.5);
            vo.setSummary("当前可用机构预报不足 2 家，无法进行多模型融合测算。"
                    + "请等待更多机构更新预报后重试。");
            vo.setStatus(STATUS_PENDING);
            vo.setModels(listModels(typhoonId));
            return vo;
        }

        // ---- 时间对齐重采样 ----
        TreeSet<LocalDateTime> axis = buildTimeAxis(usable);
        List<LocalDateTime> times = new ArrayList<LocalDateTime>(axis);
        List<double[]> fused = new ArrayList<double[]>(times.size());   // [lon, lat]
        List<Integer> fusedForce = new ArrayList<Integer>(times.size());
        List<Double> spreads = new ArrayList<Double>(times.size());

        for (LocalDateTime t : times) {
            List<double[]> samples = new ArrayList<double[]>();
            int forceSum = 0;
            int forceCount = 0;
            for (List<ForecastPath> series : usable.values()) {
                double[] p = interpolateAt(series, t);
                if (p != null) {
                    samples.add(p);
                }
                Integer f = forceAt(series, t);
                if (f != null) {
                    forceSum += f.intValue();
                    forceCount++;
                }
            }
            if (samples.isEmpty()) {
                continue;
            }
            fused.add(mean(samples));
            spreads.add(meanPairwiseKm(samples));
            fusedForce.add(Integer.valueOf(forceCount == 0 ? 0 : forceSum / forceCount));
        }

        if (fused.size() < 2) {
            vo.setConsistency(Integer.valueOf(50));
            vo.setConsistencyText("样本不足");
            vo.setConfidence(0.5);
            vo.setSummary("机构预报时刻无法对齐，暂不能生成融合路径。");
            vo.setStatus(STATUS_PENDING);
            vo.setModels(listModels(typhoonId));
            return vo;
        }

        // ---- 置信度：取离散度最大的一段（最坏情况） ----
        double maxSpread = 0D;
        for (Double s : spreads) {
            if (s != null && s.doubleValue() > maxSpread) {
                maxSpread = s.doubleValue();
            }
        }
        double scaleKm = settingDao.getDouble(KEY_CONFIDENCE_SCALE_KM, DEFAULT_CONFIDENCE_SCALE_KM);
        double consistency = 100D * (1D - clamp(maxSpread / (scaleKm <= 0D ? DEFAULT_CONFIDENCE_SCALE_KM : scaleKm), 0D, 1D));
        consistency = GeoUtil.round(clamp(consistency, 0D, MAX_CONFIDENCE), 0);
        double confidence = GeoUtil.round(consistency / 100D, 2);

        vo.setConsistency(Integer.valueOf((int) consistency));
        vo.setConsistencyText(consistencyText(consistency));
        vo.setConfidence(Double.valueOf(confidence));
        vo.setPoints(toPoints(fused));

        double[] landfall = fused.get(fused.size() - 1);
        vo.setLandingRange(landingText(landfall));
        vo.setIntensityTrend(intensityTrend(fusedForce));
        vo.setSummary(summaryOf(vo, usable.size(), maxSpread, landfall));
        vo.setStatus(STATUS_VALIDATED);

        // ---- 落库 ----
        if (saveModel != null) {
            AiPredictionRecord record = new AiPredictionRecord();
            record.setTyphoonId(Long.valueOf(typhoonId));
            record.setModelName(saveModel);
            record.setPromptVersion(DEFAULT_PROMPT_VERSION);
            record.setPredictionTime(DateUtil.now());
            record.setPredictedPath((JsonNode) JsonUtil.mapper().valueToTree(vo.getPoints()));
            record.setPredictedLandfallLon(Double.valueOf(GeoUtil.round(landfall[0], 3)));
            record.setPredictedLandfallLat(Double.valueOf(GeoUtil.round(landfall[1], 3)));
            record.setConfidenceScore(Double.valueOf(consistency));
            record.setDeviationVsAgencies(Double.valueOf(GeoUtil.round(maxSpread, 2)));
            record.setSummaryText(vo.getSummary());
            record.setStatus(STATUS_VALIDATED);
            record.setCreatedAt(DateUtil.now());
            long id = aiDao.insert(record);
            record.setRecordId(Long.valueOf(id));
        }

        vo.setModels(listModels(typhoonId));
        return vo;
    }

    /* ------------------------------------------------------------------ */
    /*  内部：数据准备                                                       */
    /* ------------------------------------------------------------------ */

    /**
     * 过滤掉点不足或时刻缺失的机构序列，并按预报时间升序排列。
     *
     * @param grouped 原始分组
     * @return 可用机构序列
     */
    private Map<String, List<ForecastPath>> normalize(Map<String, List<ForecastPath>> grouped) {
        Map<String, List<ForecastPath>> result = new LinkedHashMap<String, List<ForecastPath>>();
        for (Map.Entry<String, List<ForecastPath>> e : grouped.entrySet()) {
            List<ForecastPath> valid = new ArrayList<ForecastPath>();
            for (ForecastPath p : e.getValue()) {
                if (p.getLatitude() != null && p.getLongitude() != null && p.getForecastTime() != null) {
                    valid.add(p);
                }
            }
            if (valid.size() < 2) {
                continue;
            }
            Collections.sort(valid, new java.util.Comparator<ForecastPath>() {
                @Override
                public int compare(ForecastPath a, ForecastPath b) {
                    return a.getForecastTime().compareTo(b.getForecastTime());
                }
            });
            result.put(e.getKey(), valid);
        }
        return result;
    }

    /**
     * 构造时间轴：所有机构预报时刻的并集。
     *
     * @param usable 可用机构序列
     * @return 升序时刻集合
     */
    private TreeSet<LocalDateTime> buildTimeAxis(Map<String, List<ForecastPath>> usable) {
        TreeSet<LocalDateTime> axis = new TreeSet<LocalDateTime>();
        for (List<ForecastPath> series : usable.values()) {
            for (ForecastPath p : series) {
                axis.add(p.getForecastTime());
            }
        }
        return axis;
    }

    /**
     * 在指定时刻对某机构的预报序列做线性插值。
     *
     * <p>时刻落在该机构预报范围之外时返回 null —— 不参与融合，
     * 而不是拿端点值顶上去（那会把"没预报"伪装成"预报了和端点一样"）。
     *
     * @param series 已按时间升序的预报序列
     * @param t      目标时刻
     * @return {@code [lon, lat]}；超出范围返回 null
     */
    private double[] interpolateAt(List<ForecastPath> series, LocalDateTime t) {
        if (series == null || series.isEmpty()) {
            return null;
        }
        ForecastPath first = series.get(0);
        ForecastPath last = series.get(series.size() - 1);
        if (t.isBefore(first.getForecastTime()) || t.isAfter(last.getForecastTime())) {
            return null;
        }
        for (int i = 1; i < series.size(); i++) {
            ForecastPath prev = series.get(i - 1);
            ForecastPath cur = series.get(i);
            if (!t.isAfter(cur.getForecastTime())) {
                double span = DateUtil.hoursBetween(prev.getForecastTime(), cur.getForecastTime());
                if (span <= 0D) {
                    return new double[]{cur.getLongitude().doubleValue(), cur.getLatitude().doubleValue()};
                }
                double ratio = DateUtil.hoursBetween(prev.getForecastTime(), t) / span;
                double lon = prev.getLongitude().doubleValue()
                        + (cur.getLongitude().doubleValue() - prev.getLongitude().doubleValue()) * ratio;
                double lat = prev.getLatitude().doubleValue()
                        + (cur.getLatitude().doubleValue() - prev.getLatitude().doubleValue()) * ratio;
                return new double[]{lon, lat};
            }
        }
        return new double[]{last.getLongitude().doubleValue(), last.getLatitude().doubleValue()};
    }

    /**
     * 取某机构在指定时刻的预报风力（就近取值，不做插值——
     * 风力是分级量，插出来的小数没有物理意义）。
     *
     * @param series 预报序列
     * @param t      目标时刻
     * @return 风力等级；无数据返回 null
     */
    private Integer forceAt(List<ForecastPath> series, LocalDateTime t) {
        if (series == null || series.isEmpty()) {
            return null;
        }
        ForecastPath nearest = null;
        double bestGap = Double.MAX_VALUE;
        for (ForecastPath p : series) {
            if (p.getForecastWindForce() == null) {
                continue;
            }
            double gap = Math.abs(DateUtil.hoursBetween(p.getForecastTime(), t));
            if (gap < bestGap) {
                bestGap = gap;
                nearest = p;
            }
        }
        if (nearest == null) {
            return null;
        }
        return nearest.getForecastWindForce();
    }

    /**
     * 求坐标均值。
     *
     * @param samples 样本点集合，每项 {@code [lon, lat]}
     * @return 均值点
     */
    private double[] mean(List<double[]> samples) {
        double lon = 0D;
        double lat = 0D;
        for (double[] s : samples) {
            lon += s[0];
            lat += s[1];
        }
        return new double[]{lon / samples.size(), lat / samples.size()};
    }

    /**
     * 求样本点两两距离的平均值，用于度量机构离散程度。
     *
     * @param samples 样本点集合
     * @return 平均两两距离（km）；样本不足 2 个时返回 0
     */
    private double meanPairwiseKm(List<double[]> samples) {
        if (samples.size() < 2) {
            return 0D;
        }
        double sum = 0D;
        int pairs = 0;
        for (int i = 0; i < samples.size(); i++) {
            for (int j = i + 1; j < samples.size(); j++) {
                sum += GeoUtil.distanceKm(samples.get(i)[1], samples.get(i)[0],
                        samples.get(j)[1], samples.get(j)[0]);
                pairs++;
            }
        }
        return pairs == 0 ? 0D : sum / pairs;
    }

    /**
     * 融合路径点转前端需要的 {@code [[lon, lat], ...]} 结构。
     *
     * @param fused 融合点
     * @return 二维数组列表（保留 2 位小数）
     */
    private List<List<Double>> toPoints(List<double[]> fused) {
        List<List<Double>> points = new ArrayList<List<Double>>(fused.size());
        for (double[] p : fused) {
            List<Double> one = new ArrayList<Double>(2);
            one.add(Double.valueOf(GeoUtil.round(p[0], 2)));
            one.add(Double.valueOf(GeoUtil.round(p[1], 2)));
            points.add(one);
        }
        return points;
    }

    /**
     * 由融合路径的风力序列生成强度趋势文案。
     *
     * @param forces 风力序列
     * @return 趋势文案
     */
    private String intensityTrend(List<Integer> forces) {
        if (forces.isEmpty()) {
            return "强度资料不足，暂无法判断趋势。";
        }
        int first = forces.get(0).intValue();
        int peak = 0;
        for (Integer f : forces) {
            if (f != null && f.intValue() > peak) {
                peak = f.intValue();
            }
        }
        int last = forces.get(forces.size() - 1).intValue();
        StringBuilder sb = new StringBuilder();
        sb.append("融合路径峰值风力 ").append(peak).append(" 级");
        if (peak >= STRONG_FORCE) {
            sb.append("（强台风级及以上）");
        }
        sb.append("；");
        if (last < peak) {
            sb.append("登陆后逐步减弱，末段降至 ").append(last).append(" 级。");
        } else if (last > first) {
            sb.append("全程强度持续增强，末段升至 ").append(last).append(" 级。");
        } else {
            sb.append("强度总体维持，末段 ").append(last).append(" 级。");
        }
        return sb.toString();
    }

    /**
     * 置信度 → 文字评价。
     *
     * @param consistency 一致性（0—100）
     * @return 很好 / 较好 / 一般 / 较低
     */
    private String consistencyText(double consistency) {
        if (consistency >= 85D) {
            return "很好";
        }
        if (consistency >= 70D) {
            return "较好";
        }
        return consistency >= 50D ? "一般" : "较低";
    }

    /**
     * 由路径末点反推登陆点描述（复用城市表做最近邻匹配）。
     *
     * @param landfall 末点 {@code [lon, lat]}
     * @return 形如「厦门沿海」；无法判定时给出经纬度描述
     */
    private String landingText(double[] landfall) {
        if (landfall == null || landfall.length < 2) {
            return null;
        }
        City nearest = null;
        double minKm = Double.MAX_VALUE;
        for (City c : cityService.asMap().values()) {
            if (c.getLatitude() == null || c.getLongitude() == null) {
                continue;
            }
            double km = GeoUtil.distanceKm(landfall[1], landfall[0],
                    c.getLatitude().doubleValue(), c.getLongitude().doubleValue());
            if (km < minKm) {
                minKm = km;
                nearest = c;
            }
        }
        if (nearest == null || minKm > 220D) {
            return GeoUtil.round(landfall[1], 2) + "°N, " + GeoUtil.round(landfall[0], 2) + "°E 附近海域";
        }
        boolean coastal = nearest.getCoastal() != null && nearest.getCoastal().booleanValue();
        return nearest.getCityName() + (coastal ? "沿海" : "一带");
    }

    /**
     * 生成解读文案。
     *
     * @param vo      测算结果
     * @param agencies 参与融合的机构数
     * @param spreadKm 最大离散度（km）
     * @param landfall 融合末点
     * @return 解读文案
     */
    private String summaryOf(AiPredictionVO vo, int agencies, double spreadKm, double[] landfall) {
        StringBuilder sb = new StringBuilder();
        sb.append("融合 ").append(agencies).append(" 家机构预报，");
        sb.append("登陆点大概率位于").append(vo.getLandingRange() == null ? "上述经纬度附近" : vo.getLandingRange());
        sb.append("。机构间最大分歧约 ").append(Math.round(spreadKm)).append(" km，");
        if (spreadKm > 200D) {
            sb.append("分歧较大，当前结论不确定性高，需持续跟踪最新预报。");
        } else if (spreadKm > 80D) {
            sb.append("分歧处于常见水平，结论具备一定参考价值。");
        } else {
            sb.append("机构高度一致，结论相对可靠。");
        }
        sb.append("AI 测算仅供科普参考，不替代官方预报。");
        return sb.toString();
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
