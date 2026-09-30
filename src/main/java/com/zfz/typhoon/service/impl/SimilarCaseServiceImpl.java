package com.zfz.typhoon.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.zfz.typhoon.dao.DisasterReportDao;
import com.zfz.typhoon.dao.SimilarCaseMatchDao;
import com.zfz.typhoon.dao.SystemSettingDao;
import com.zfz.typhoon.dao.TyphoonDao;
import com.zfz.typhoon.dao.TyphoonTrackDao;
import com.zfz.typhoon.dao.WindCircleDao;
import com.zfz.typhoon.dao.impl.DisasterReportDaoImpl;
import com.zfz.typhoon.dao.impl.SimilarCaseMatchDaoImpl;
import com.zfz.typhoon.dao.impl.SystemSettingDaoImpl;
import com.zfz.typhoon.dao.impl.TyphoonDaoImpl;
import com.zfz.typhoon.dao.impl.TyphoonTrackDaoImpl;
import com.zfz.typhoon.dao.impl.WindCircleDaoImpl;
import com.zfz.typhoon.model.DisasterReport;
import com.zfz.typhoon.model.SimilarCaseMatch;
import com.zfz.typhoon.model.Typhoon;
import com.zfz.typhoon.model.TyphoonTrack;
import com.zfz.typhoon.model.WindCircle;
import com.zfz.typhoon.model.vo.SimilarCaseVO;
import com.zfz.typhoon.service.SimilarCaseService;
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
 * 历史相似台风检索实现。
 *
 * <p>五个维度的打分口径见 {@link SimilarCaseService} 的类注释。这里补充说明
 * <b>路径形态相似度</b>的算法，它是本模块唯一非平凡的部分：
 *
 * <ol>
 *   <li>把两条轨迹都按<b>弧长等分</b>重采样成 {@value #SHAPE_SAMPLES} 个点。
 *       直接比较原始点不可行——不同台风的观测频次与总时长都不一样；</li>
 *   <li>把每条的坐标换算成以起点为原点的局部平面坐标（km）；</li>
 *   <li>各自<b>除以自身路径总长</b>，即缩放到单位长度。
 *       这一步去掉了"路径走得多远"的影响，只保留"弯成什么形状"——</li>
 *   <li>此时两条单位化曲线逐点求距离并取平均，即形状差。
 *       经验阈值 {@value #SHAPE_TOLERANCE}：平均偏差达到路径长度的 30% 视为完全不同。</li>
 * </ol>
 *
 * <p>这样做的好处是<b>可解释</b>：答辩被问"相似度怎么算的"时能讲清每一步，
 * 而不是"调了个模型"。
 *
 * @author 追风者项目组
 */
public class SimilarCaseServiceImpl implements SimilarCaseService {

    /** 形状重采样点数（越大越精细，6 个点在台风尺度上已足够刻画转向） */
    private static final int SHAPE_SAMPLES = 6;

    /** 形状差异容忍度：平均偏差达到路径长度的该比例时相似度记为 0 */
    private static final double SHAPE_TOLERANCE = 0.30D;

    /* ----------------------------- 维度权重 ----------------------------- */

    private static final double W_PATH = 0.35D;
    private static final double W_INTENSITY = 0.25D;
    private static final double W_WIND_CIRCLE = 0.15D;
    private static final double W_SEASON = 0.15D;
    private static final double W_SPEED = 0.10D;

    /* --------------------------- 各维度参考尺度 --------------------------- */

    /** 峰值强度参考尺度（风力等级） */
    private static final double REF_WIND_FORCE = 10D;
    /** 移速参考尺度（km/h） */
    private static final double REF_SPEED = 20D;
    /** 风圈半径参考尺度（km） */
    private static final double REF_WIND_RADIUS = 200D;
    /** 季节参考尺度（月）；12 个月环形，最大半程为 6 */
    private static final double REF_MONTH = 6D;

    /* ------------------------------ 默认配置 ------------------------------ */

    private static final int DEFAULT_TOP_N = 5;
    private static final double DEFAULT_MIN_SCORE = 0.50D;
    private static final int TOP_N_MIN = 1;
    private static final int TOP_N_MAX = 20;

    /** 7 级风圈——最外圈，最能体现台风的整体影响尺度 */
    private static final int REFERENCE_LEVEL = 7;

    private final TyphoonDao typhoonDao;
    private final TyphoonTrackDao trackDao;
    private final WindCircleDao windCircleDao;
    private final DisasterReportDao disasterDao;
    private final SimilarCaseMatchDao matchDao;
    private final SystemSettingDao settingDao;
    private final TyphoonService typhoonService;

    /**
     * 生产用构造：按默认实现装配依赖。
     * <p>之所以把它拆成"无参 + 带参"两个构造，是为了给单元测试留出注入替身的缝隙：
     * 依赖全部在字段初始化里 new 出来的话，测试就必须连上真实 MySQL 才能跑，
     * 单元测试也就退化成了集成测试。
     */
    public SimilarCaseServiceImpl() {
        this(new TyphoonDaoImpl(), new TyphoonTrackDaoImpl(), new WindCircleDaoImpl(), new DisasterReportDaoImpl(), new SimilarCaseMatchDaoImpl(), new SystemSettingDaoImpl(), new TyphoonServiceImpl());
    }

    /**
     * 测试用构造：注入替身依赖（Mockito 生成的 mock 或手写桩）。
     * <p>参数顺序与上方字段声明一致，均为本服务运行所需的协作者。
     */
    public SimilarCaseServiceImpl(TyphoonDao typhoonDao, TyphoonTrackDao trackDao, WindCircleDao windCircleDao, DisasterReportDao disasterDao, SimilarCaseMatchDao matchDao, SystemSettingDao settingDao, TyphoonService typhoonService) {
        this.typhoonDao = typhoonDao;
        this.trackDao = trackDao;
        this.windCircleDao = windCircleDao;
        this.disasterDao = disasterDao;
        this.matchDao = matchDao;
        this.settingDao = settingDao;
        this.typhoonService = typhoonService;
    }

    @Override
    public List<SimilarCaseVO> findSimilar(long typhoonId) {
        return findSimilar(typhoonId, settingDao.getInt(KEY_TOP_N, DEFAULT_TOP_N));
    }

    @Override
    public List<SimilarCaseVO> findSimilar(long typhoonId, int topN) {
        // 先校验收敛到 1—20，避免前端传 0 或 999 时把整库都捞出来
        int limit = topN < TOP_N_MIN ? TOP_N_MIN : (topN > TOP_N_MAX ? TOP_N_MAX : topN);
        double minScore = settingDao.getDouble(KEY_MIN_SCORE, DEFAULT_MIN_SCORE);

        typhoonService.requireTyphoon(typhoonId);

        double[] currentShape = shapeOf(typhoonId);
        if (currentShape == null) {
            // 当前台风还没有轨迹，无法从中提取路径形态特征，直接返回空
            return new ArrayList<SimilarCaseVO>();
        }
        Typhoon current = typhoonDao.findById(typhoonId);

        List<SimilarCaseVO> scored = new ArrayList<SimilarCaseVO>();
        for (Typhoon candidate : typhoonDao.listAll()) {
            if (candidate == null || candidate.getTyphoonId() == null
                    || candidate.getTyphoonId().longValue() == typhoonId) {
                continue;
            }
            SimilarCaseMatch match = compare(current, currentShape, candidate);
            if (match == null || match.getSimilarityScore() == null) {
                continue;
            }
            if (match.getSimilarityScore().doubleValue() < minScore) {
                continue;
            }
            scored.add(toVO(match, candidate));
        }

        Collections.sort(scored, new Comparator<SimilarCaseVO>() {
            @Override
            public int compare(SimilarCaseVO a, SimilarCaseVO b) {
                return Double.compare(value(b.getScore()), value(a.getScore()));
            }
        });

        return scored.size() > limit
                ? new ArrayList<SimilarCaseVO>(scored.subList(0, limit))
                : scored;
    }

    @Override
    public SimilarCaseMatch score(long currentTyphoonId, long historyTyphoonId) {
        Typhoon current = typhoonService.requireTyphoon(currentTyphoonId);
        Typhoon history = typhoonService.requireTyphoon(historyTyphoonId);
        return compare(current, shapeOf(currentTyphoonId), history);
    }

    @Override
    public int saveMatches(long typhoonId) {
        List<SimilarCaseVO> list = findSimilar(typhoonId, TOP_N_MAX);
        if (list.isEmpty()) {
            return 0;
        }
        List<SimilarCaseMatch> matches = new ArrayList<SimilarCaseMatch>(list.size());
        for (SimilarCaseVO vo : list) {
            SimilarCaseMatch m = new SimilarCaseMatch();
            m.setCurrentTyphoonId(Long.valueOf(typhoonId));
            m.setHistoryTyphoonId(vo.getTyphoonId());
            m.setSimilarityScore(vo.getScore());
            m.setPathSimilarity(vo.getMatchDims().get("path"));
            m.setPeakIntensitySimilarity(vo.getMatchDims().get("intensity"));
            m.setSeasonSimilarity(vo.getMatchDims().get("season"));
            m.setMoveSpeedSimilarity(vo.getMatchDims().get("speed"));
            m.setWindCircleSimilarity(vo.getMatchDims().get("windCircle"));

            Map<String, Object> detail = new LinkedHashMap<String, Object>();
            detail.put("diffHint", vo.getDiffHint());
            detail.put("differences", vo.getDifferences());
            detail.put("note", vo.getNote());
            m.setMatchResult((JsonNode) JsonUtil.mapper().valueToTree(detail));

            m.setCreatedAt(DateUtil.now());
            matches.add(m);
        }
        return matchDao.upsertAll(matches);
    }

    @Override
    public List<SimilarCaseMatch> listSaved(long typhoonId) {
        return matchDao.listByCurrentTyphoon(typhoonId);
    }

    /* ------------------------------------------------------------------ */
    /*  核心：两两比较                                                       */
    /* ------------------------------------------------------------------ */

    /**
     * 用当前台风的"已算好的形状"与某个历史台风比一次，得到五维打分。
     *
     * @param current      当前台风
     * @param currentShape 当前台风单位化后的形状特征
     * @param history      历史台风
     * @return 匹配记录（未落库）；历史台风无轨迹时返回 null
     */
    private SimilarCaseMatch compare(Typhoon current, double[] currentShape, Typhoon history) {
        List<TyphoonTrack> historyTracks = trackDao.listByTyphoon(history.getTyphoonId().longValue());
        double[] historyShape = toShape(historyTracks);
        if (currentShape == null || historyShape == null) {
            return null;
        }

        double path = pathSimilarity(currentShape, historyShape);
        double intensity = ratioSimilarity(current.getMaxWindForce(), history.getMaxWindForce(),
                REF_WIND_FORCE);
        double season = seasonSimilarity(current.getGenerateTime(), history.getGenerateTime());
        double speed = ratioSimilarity(avgSpeed(typhoonIdOf(current)), avgSpeed(typhoonIdOf(history)),
                REF_SPEED);
        double windCircle = ratioSimilarity(avgRadius(typhoonIdOf(current)),
                avgRadius(typhoonIdOf(history)), REF_WIND_RADIUS);

        SimilarCaseMatch match = new SimilarCaseMatch();
        match.setCurrentTyphoonId(typhoonIdOf(current));
        match.setHistoryTyphoonId(typhoonIdOf(history));
        match.setPathSimilarity(Double.valueOf(round4(path)));
        match.setPeakIntensitySimilarity(Double.valueOf(round4(intensity)));
        match.setSeasonSimilarity(Double.valueOf(round4(season)));
        match.setMoveSpeedSimilarity(Double.valueOf(round4(speed)));
        match.setWindCircleSimilarity(Double.valueOf(round4(windCircle)));

        double total = W_PATH * path + W_INTENSITY * intensity
                + W_WIND_CIRCLE * windCircle + W_SEASON * season + W_SPEED * speed;
        match.setSimilarityScore(Double.valueOf(round4(clamp(total, 0D, 1D))));
        match.setCreatedAt(DateUtil.now());
        return match;
    }

    /**
     * 把匹配记录 + 历史台风的灾情组装成前端视图对象。
     *
     * @param match 匹配记录
     * @param history 历史台风
     * @return 视图对象
     */
    private SimilarCaseVO toVO(SimilarCaseMatch match, Typhoon history) {
        SimilarCaseVO vo = new SimilarCaseVO();
        vo.setTyphoonId(match.getHistoryTyphoonId());
        vo.setNameCn(history.getChineseName());
        vo.setNameEn(history.getEnglishName());
        vo.setYear(Integer.valueOf(DateUtil.yearOf(history.getGenerateTime())));
        vo.setScore(match.getSimilarityScore());

        Map<String, Double> dims = new LinkedHashMap<String, Double>();
        dims.put("path", match.getPathSimilarity());
        dims.put("intensity", match.getPeakIntensitySimilarity());
        dims.put("season", match.getSeasonSimilarity());
        dims.put("speed", match.getMoveSpeedSimilarity());
        dims.put("windCircle", match.getWindCircleSimilarity());
        vo.setMatchDims(dims);

        DisasterReport report = disasterDao.findLatestByTyphoon(history.getTyphoonId().longValue());
        if (report != null) {
            vo.setDisaster(toDisasterBrief(report));
        }

        vo.getDifferences().addAll(differences(match));
        vo.setDiffHint(diffHint(match));
        return vo;
    }

    /**
     * 把灾情报告转成前端视图对象。
     *
     * <p>数据库 {@code disaster_reports} 只有一整段 {@code disaster_summary} 文本，
     * 没有结构化的"降雨 / 内涝 / 风暴潮 / 现场情景"字段。这里按关键词从综述中
     * 抽取归类，抽不到就留空由前端降级显示综述原文——<b>不编造数据</b>。
     *
     * @param report 灾情报告实体
     * @return 灾情摘要视图对象
     */
    private SimilarCaseVO.DisasterBrief toDisasterBrief(DisasterReport report) {
        SimilarCaseVO.DisasterBrief brief = new SimilarCaseVO.DisasterBrief();
        brief.setProvinces(report.getAffectedProvinces());
        brief.setCity(report.getMainCityName());
        brief.setSummary(report.getDisasterSummary());
        brief.setSource(report.getReportSource());
        brief.setLoss(report.getDirectEconomicLoss());
        brief.setAffectedPopulation(report.getAffectedPopulation());
        brief.setCasualties(report.getCasualties());

        String text = report.getDisasterSummary() == null ? "" : report.getDisasterSummary();
        if (text.contains("暴雨") || text.contains("降雨") || text.contains("降水")) {
            brief.setRainfall("见综述：强降雨过程");
        }
        if (text.contains("内涝") || text.contains("积水")) {
            brief.setFlood("见综述：出现内涝");
        }
        if (text.contains("风暴潮") || text.contains("海水倒灌") || text.contains("超警戒")) {
            brief.setStormSurge("见综述：风暴潮影响");
        }
        if (text.contains("倒伏") || text.contains("停电") || text.contains("断电")) {
            brief.setScene("见综述：树木倒伏 / 供电中断");
        }
        return brief;
    }

    /**
     * 生成"差异提示"——相似不等于相同，必须让用户看到差别在哪。
     *
     * @param match 匹配记录
     * @return 差异提示文案
     */
    private String diffHint(SimilarCaseMatch match) {
        List<String> diffs = differences(match);
        if (diffs.isEmpty()) {
            return "各维度均较接近，可作类比参考。";
        }
        StringBuilder sb = new StringBuilder("主要差异：");
        for (int i = 0; i < diffs.size(); i++) {
            if (i > 0) {
                sb.append("；");
            }
            sb.append(diffs.get(i));
        }
        sb.append("。");
        return sb.toString();
    }

    /**
     * 判定"某维度差异较大"的相似度阈值。
     */
    private static final double DIFF_THRESHOLD = 0.70D;

    /**
     * 列出相似度明显偏低的维度（低于 {@value #DIFF_THRESHOLD}）。
     *
     * @param match 匹配记录
     * @return 差异描述列表
     */
    private List<String> differences(SimilarCaseMatch match) {
        List<String> diffs = new ArrayList<String>();
        appendDiff(diffs, "路径走向", match.getPathSimilarity());
        appendDiff(diffs, "峰值强度", match.getPeakIntensitySimilarity());
        appendDiff(diffs, "登陆季节", match.getSeasonSimilarity());
        appendDiff(diffs, "移动速度", match.getMoveSpeedSimilarity());
        appendDiff(diffs, "风圈规模", match.getWindCircleSimilarity());
        return diffs;
    }

    /**
     * 若某维度相似度偏低，追加一条差异描述。
     *
     * @param sink 收集容器
     * @param name 维度名
     * @param value 相似度
     */
    private void appendDiff(List<String> sink, String name, Double value) {
        if (value == null || value.doubleValue() < DIFF_THRESHOLD) {
            sink.add(name + "差异较大");
        }
    }

    /* ------------------------------------------------------------------ */
    /*  各维度相似度计算                                                     */
    /* ------------------------------------------------------------------ */

    /**
     * 路径形态相似度。
     *
     * @param a 单位化形状 A（长度 2*{@value #SHAPE_SAMPLES}）
     * @param b 单位化形状 B
     * @return 相似度 0—1
     */
    private double pathSimilarity(double[] a, double[] b) {
        int n = Math.min(a.length, b.length) / 2;
        if (n == 0) {
            return 0D;
        }
        double sum = 0D;
        for (int i = 0; i < n; i++) {
            double dx = a[i * 2] - b[i * 2];
            double dy = a[i * 2 + 1] - b[i * 2 + 1];
            sum += Math.sqrt(dx * dx + dy * dy);
        }
        double mean = sum / n;
        return clamp(1D - mean / SHAPE_TOLERANCE, 0D, 1D);
    }

    /**
     * 取某台风的单位化路径形状特征。
     *
     * @param typhoonId 台风ID
     * @return 形状特征数组；轨迹不足 2 点或路径长度为 0 时返回 null
     */
    private double[] shapeOf(long typhoonId) {
        return toShape(trackDao.listByTyphoon(typhoonId));
    }

    /**
     * 把轨迹点序列转换成"单位化路径形状特征"。
     *
     * @param tracks 轨迹点（按观测时间升序）
     * @return 长度 2*{@value #SHAPE_SAMPLES} 的数组 {@code [x0,y0,x1,y1,...]}；
     *         数据不足时返回 null
     */
    private double[] toShape(List<TyphoonTrack> tracks) {
        List<TyphoonTrack> valid = new ArrayList<TyphoonTrack>();
        for (TyphoonTrack t : tracks) {
            if (t != null && t.getLatitude() != null && t.getLongitude() != null) {
                valid.add(t);
            }
        }
        if (valid.size() < 2) {
            return null;
        }
        Collections.sort(valid, new Comparator<TyphoonTrack>() {
            @Override
            public int compare(TyphoonTrack a, TyphoonTrack b) {
                LocalDateTime ta = a.getObsTime();
                LocalDateTime tb = b.getObsTime();
                if (ta == null || tb == null) {
                    return 0;
                }
                return ta.compareTo(tb);
            }
        });

        // 1) 转局部平面坐标（km），以首点为原点
        double lat0 = valid.get(0).getLatitude().doubleValue();
        double lon0 = valid.get(0).getLongitude().doubleValue();
        double kmPerLon = GeoUtil.kmPerLon(lat0);

        double[] xs = new double[valid.size()];
        double[] ys = new double[valid.size()];
        for (int i = 0; i < valid.size(); i++) {
            xs[i] = (valid.get(i).getLongitude().doubleValue() - lon0) * kmPerLon;
            ys[i] = (valid.get(i).getLatitude().doubleValue() - lat0) * GeoUtil.KM_PER_LAT;
        }

        // 2) 累计弧长
        double[] cum = new double[valid.size()];
        for (int i = 1; i < valid.size(); i++) {
            double dx = xs[i] - xs[i - 1];
            double dy = ys[i] - ys[i - 1];
            cum[i] = cum[i - 1] + Math.sqrt(dx * dx + dy * dy);
        }
        double total = cum[cum.length - 1];
        if (total <= 0D) {
            return null;
        }

        // 3) 按弧长等分重采样，并除以总长做单位化
        double[] shape = new double[SHAPE_SAMPLES * 2];
        int cursor = 0;
        for (int s = 0; s < SHAPE_SAMPLES; s++) {
            double target = total * s / (SHAPE_SAMPLES - 1);
            while (cursor < cum.length - 2 && cum[cursor + 1] < target) {
                cursor++;
            }
            double segStart = cum[cursor];
            double segEnd = cum[cursor + 1];
            double t = segEnd - segStart <= 0D ? 0D : (target - segStart) / (segEnd - segStart);
            double x = xs[cursor] + (xs[cursor + 1] - xs[cursor]) * t;
            double y = ys[cursor] + (ys[cursor + 1] - ys[cursor]) * t;
            shape[s * 2] = x / total;
            shape[s * 2 + 1] = y / total;
        }
        return shape;
    }

    /**
     * 通用"差值越小越相似"打分。
     *
     * @param a      值 A（可为 null）
     * @param b      值 B（可为 null）
     * @param normal 参考尺度
     * @return 相似度 0—1；任一值为 null 或 normal 非法时返回 0
     */
    private double ratioSimilarity(Number a, Number b, double normal) {
        if (a == null || b == null || normal <= 0D) {
            return 0D;
        }
        double diff = Math.abs(a.doubleValue() - b.doubleValue());
        return clamp(1D - diff / normal, 0D, 1D);
    }

    /**
     * 季节相似度（按 12 个月环形取最短月差）。
     *
     * @param a 时间 A
     * @param b 时间 B
     * @return 相似度 0—1；任一时间为 null 时返回 0
     */
    private double seasonSimilarity(LocalDateTime a, LocalDateTime b) {
        if (a == null || b == null) {
            return 0D;
        }
        int m1 = a.getMonthValue();
        int m2 = b.getMonthValue();
        int diff = Math.abs(m1 - m2);
        diff = Math.min(diff, 12 - diff);
        return clamp(1D - diff / REF_MONTH, 0D, 1D);
    }

    /**
     * 取台风全过程的平均移速。
     *
     * @param typhoonId 台风ID
     * @return 平均移速（km/h）；数据不足时返回 null
     */
    private Double avgSpeed(long typhoonId) {
        List<TyphoonTrack> tracks = trackDao.listByTyphoon(typhoonId);
        List<TyphoonTrack> valid = new ArrayList<TyphoonTrack>();
        for (TyphoonTrack t : tracks) {
            if (t != null && t.getLatitude() != null && t.getLongitude() != null
                    && t.getObsTime() != null) {
                valid.add(t);
            }
        }
        if (valid.size() < 2) {
            return null;
        }
        Collections.sort(valid, new Comparator<TyphoonTrack>() {
            @Override
            public int compare(TyphoonTrack a, TyphoonTrack b) {
                return a.getObsTime().compareTo(b.getObsTime());
            }
        });

        double km = 0D;
        for (int i = 1; i < valid.size(); i++) {
            km += GeoUtil.distanceKm(valid.get(i - 1).getLatitude().doubleValue(),
                    valid.get(i - 1).getLongitude().doubleValue(),
                    valid.get(i).getLatitude().doubleValue(),
                    valid.get(i).getLongitude().doubleValue());
        }
        double hours = DateUtil.hoursBetween(valid.get(0).getObsTime(),
                valid.get(valid.size() - 1).getObsTime());
        return hours <= 0D ? null : Double.valueOf(km / hours);
    }

    /**
     * 取台风最新一次观测的 7 级风圈平均半径。
     *
     * @param typhoonId 台风ID
     * @return 平均半径（km）；无风圈数据时返回 null
     */
    private Double avgRadius(long typhoonId) {
        WindCircle circle = windCircleDao.findLatest(typhoonId);
        if (circle == null) {
            return null;
        }
        double r = circle.averageRadius(REFERENCE_LEVEL);
        return r <= 0D ? null : Double.valueOf(r);
    }

    /* ------------------------------------------------------------------ */
    /*  小工具                                                              */
    /* ------------------------------------------------------------------ */

    /**
     * 取台风主键（非空）。
     *
     * @param typhoon 台风
     * @return 主键
     */
    private long typhoonIdOf(Typhoon typhoon) {
        return typhoon.getTyphoonId() == null ? 0L : typhoon.getTyphoonId().longValue();
    }

    /**
     * 保留 4 位小数（相似度列的精度是 decimal(5,4)）。
     *
     * @param v 原始值
     * @return 舍入结果
     */
    private double round4(double v) {
        return GeoUtil.round(v, 4);
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

    /**
     * 取 Double 的原始值，null 视为 0。
     *
     * @param v 包装值
     * @return 原始值
     */
    private double value(Double v) {
        return v == null ? 0D : v.doubleValue();
    }
}
