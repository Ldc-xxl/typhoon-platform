package com.zfz.typhoon.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zfz.typhoon.dao.HistoricalComparisonDao;
import com.zfz.typhoon.dao.impl.HistoricalComparisonDaoImpl;
import com.zfz.typhoon.model.HistoricalComparison;
import com.zfz.typhoon.model.SimilarCaseMatch;
import com.zfz.typhoon.model.Typhoon;
import com.zfz.typhoon.model.TyphoonTrack;
import com.zfz.typhoon.service.ComparisonService;
import com.zfz.typhoon.service.SimilarCaseService;
import com.zfz.typhoon.service.TrackService;
import com.zfz.typhoon.service.TyphoonService;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.DateUtil;
import com.zfz.typhoon.util.GeoUtil;
import com.zfz.typhoon.util.JsonUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 历史台风对比业务实现。
 *
 * <h3>路径偏差怎么算</h3>
 * 「路径偏差 N km」最有说服力的算法是<b>双向平均最近点距离</b>
 * （离散化的 Fréchet / 平均 Hausdorff 距离）：
 * 把两条轨迹各重采样若干点，对 A 的每个点找工作 B 中最近的点取距离，
 * 再反过来做一次，两者取平均。
 *
 * <p>为什么不直接用"起点距离"或"终点距离"：
 * 两条路径可能起点终点都很近，中间却一个走北一个走南，实际影响完全不同。
 * 逐点最近距离能把这种"兜圈子"的差异反映出来。
 *
 * <p>为了防止长轨迹把计算量拉爆，采样上限固定为
 * {@value #MAX_SAMPLE_POINTS} 点（按索引等间隔抽取），
 * 对结论精度影响可忽略。
 *
 * @author 追风者项目组
 * @see ComparisonService
 */
public class ComparisonServiceImpl implements ComparisonService {

    /** 每条轨迹参与距离计算的最大采样点数 */
    private static final int MAX_SAMPLE_POINTS = 40;

    /** 对比名称默认长度上限，对齐 comparison_name varchar(100) */
    private static final int NAME_MAX = 100;

    /** 相似度结论阈值 */
    private static final double SIM_HIGH = 0.80D;
    private static final double SIM_MIDDLE = 0.60D;

    private final HistoricalComparisonDao comparisonDao;
    private final TyphoonService typhoonService;
    private final TrackService trackService;
    private final SimilarCaseService similarCaseService;

    /**
     * 生产用构造：按默认实现装配依赖。
     * <p>之所以把它拆成"无参 + 带参"两个构造，是为了给单元测试留出注入替身的缝隙：
     * 依赖全部在字段初始化里 new 出来的话，测试就必须连上真实 MySQL 才能跑，
     * 单元测试也就退化成了集成测试。
     */
    public ComparisonServiceImpl() {
        this(new HistoricalComparisonDaoImpl(), new TyphoonServiceImpl(), new TrackServiceImpl(), new SimilarCaseServiceImpl());
    }

    /**
     * 测试用构造：注入替身依赖（Mockito 生成的 mock 或手写桩）。
     * <p>参数顺序与上方字段声明一致，均为本服务运行所需的协作者。
     */
    public ComparisonServiceImpl(HistoricalComparisonDao comparisonDao, TyphoonService typhoonService, TrackService trackService, SimilarCaseService similarCaseService) {
        this.comparisonDao = comparisonDao;
        this.typhoonService = typhoonService;
        this.trackService = trackService;
        this.similarCaseService = similarCaseService;
    }

    @Override
    public HistoricalComparison create(long userId, String comparisonName,
                                      long primaryTyphoonId, List<Long> compareTyphoonIds) {
        Typhoon primary = typhoonService.requireTyphoon(primaryTyphoonId);
        if (compareTyphoonIds == null || compareTyphoonIds.isEmpty()) {
            throw BusinessException.badRequest("至少要指定一个对比台风");
        }
        // 去重排序：同一个台风被传两次不该产生两份对比结果
        Set<Long> ids = new LinkedHashSet<Long>();
        for (Long id : compareTyphoonIds) {
            if (id == null) {
                continue;
            }
            if (id.longValue() == primaryTyphoonId) {
                throw BusinessException.badRequest("对比台风不能与主台风相同：" + primaryTyphoonId);
            }
            ids.add(id);
        }
        if (ids.isEmpty()) {
            throw BusinessException.badRequest("至少要指定一个有效的对比台风");
        }
        if (ids.size() + 1 > MAX_TYPHOONS) {
            throw BusinessException.badRequest("单次对比最多包含 " + MAX_TYPHOONS + " 个台风");
        }

        List<Typhoon> others = new ArrayList<Typhoon>(ids.size());
        for (Long id : ids) {
            others.add(typhoonService.requireTyphoon(id.longValue()));
        }

        HistoricalComparison entity = new HistoricalComparison();
        entity.setUserId(Long.valueOf(userId));
        entity.setPrimaryTyphoonId(Long.valueOf(primaryTyphoonId));
        entity.setComparisonName(resolveName(comparisonName, primary, others));
        entity.setCompareTyphoonIds(idArray(ids));
        entity.setResultSummary(computeSummary(primary, others));
        entity.setCreatedAt(DateUtil.now());

        long id = comparisonDao.insert(entity);
        entity.setComparisonId(Long.valueOf(id));
        return entity;
    }

    @Override
    public List<HistoricalComparison> list(long userId) {
        return comparisonDao.listByUser(userId);
    }

    @Override
    public List<HistoricalComparison> listByPrimaryTyphoon(long typhoonId) {
        return comparisonDao.listByPrimaryTyphoon(typhoonId);
    }

    @Override
    public HistoricalComparison get(long comparisonId, long userId) {
        HistoricalComparison c = comparisonDao.findById(comparisonId);
        if (c == null) {
            throw BusinessException.notFound("对比任务不存在：" + comparisonId);
        }
        if (c.getUserId() == null || c.getUserId().longValue() != userId) {
            throw BusinessException.forbidden("无权查看他人的对比任务");
        }
        return c;
    }

    @Override
    public int rename(long comparisonId, String name, long userId) {
        if (name == null || name.trim().isEmpty()) {
            throw BusinessException.badRequest("对比名称不能为空");
        }
        String n = name.trim();
        if (n.length() > NAME_MAX) {
            throw BusinessException.badRequest("对比名称不能超过 " + NAME_MAX + " 个字符");
        }
        HistoricalComparison c = get(comparisonId, userId);
        return comparisonDao.rename(c.getComparisonId().longValue(), n, userId);
    }

    @Override
    public int delete(long comparisonId, long userId) {
        get(comparisonId, userId);
        return comparisonDao.deleteByIdAndUser(comparisonId, userId);
    }

    /* ==================================================================== */
    /*  内部：结论计算                                                       */
    /* ==================================================================== */

    /**
     * 计算对比结论并写入 {@code result_summary}。
     *
     * <p>结构对齐 {@code historical_comparisons} 的既有数据：
     * {@code {conclusion, path_diff_km, intensity_diff}}，
     * 并额外给出 {@code similarity} 与 {@code details} 供前端做明细表。
     *
     * @param primary 主台风
     * @param others  对比台风
     * @return 结论 JSON
     */
    private JsonNode computeSummary(Typhoon primary, List<Typhoon> others) {
        ObjectNode root = JsonUtil.mapper().createObjectNode();
        ArrayNode details = JsonUtil.mapper().createArrayNode();

        double devSum = 0D;
        int devCount = 0;
        double simSum = 0D;
        int simCount = 0;
        StringBuilder conclusion = new StringBuilder();

        for (Typhoon other : others) {
            double deviationKm = pathDeviationKm(primary, other);
            int intensityDiff = Math.abs(force(primary) - force(other));

            Double similarity = null;
            try {
                SimilarCaseMatch match = similarCaseService.score(
                        primary.getTyphoonId().longValue(), other.getTyphoonId().longValue());
                if (match != null && match.getSimilarityScore() != null) {
                    similarity = match.getSimilarityScore();
                }
            } catch (RuntimeException ignored) {
                // 打分失败（如对方无轨迹）不影响主结论：偏差与强度差仍可给
            }

            ObjectNode one = details.addObject();
            one.put("typhoonId", other.getTyphoonId().longValue());
            one.put("typhoonName", other.getChineseName() == null ? "" : other.getChineseName());
            one.put("pathDiffKm", GeoUtil.round(deviationKm, 1));
            one.put("intensityDiff", intensityDiff);
            if (similarity != null) {
                one.put("similarity", GeoUtil.round(similarity.doubleValue(), 2));
            }

            devSum += deviationKm;
            devCount++;
            if (similarity != null) {
                simSum += similarity.doubleValue();
                simCount++;
            }

            conclusion.append(name(other)).append("：路径偏差约 ")
                    .append(Math.round(deviationKm)).append(" km，强度差 ")
                    .append(intensityDiff).append(" 级；");
        }

        double avgDev = devCount == 0 ? 0D : devSum / devCount;
        Double avgSim = simCount == 0 ? null : Double.valueOf(simSum / simCount);

        root.put("conclusion", conclusionText(avgDev, avgSim));
        root.put("path_diff_km", GeoUtil.round(avgDev, 1));
        root.put("intensity_diff", intensityDiffAvg(details));
        if (avgSim != null) {
            root.put("similarity", GeoUtil.round(avgSim.doubleValue(), 2));
        }
        root.put("primaryTyphoonId", primary.getTyphoonId().longValue());
        root.put("primaryTyphoonName", name(primary));
        root.put("comparedCount", devCount);
        root.put("generatedAt", DateUtil.format(DateUtil.now()));
        root.set("details", details);
        return root;
    }

    /**
     * 生成一句话结论。
     *
     * <p>结论只由可解释的指标组合得出：路径偏差越小、平均相似度越高，结论越"像"。
     * 不用更花哨的措辞，因为对比报告是给人做判断用的，
     * 结论一旦模糊反而会增加理解成本。
     *
     * @param avgDev 平均路径偏差（km）
     * @param avgSim 平均相似度（0—1），可为 null
     * @return 结论文案
     */
    private String conclusionText(double avgDev, Double avgSim) {
        if (avgSim != null && avgSim.doubleValue() >= SIM_HIGH) {
            return "路径高度相似（平均偏差约 " + Math.round(avgDev) + " km），可参考该历史台风的防灾处置经验。";
        }
        if (avgSim != null && avgSim.doubleValue() >= SIM_MIDDLE) {
            return "路径基本相似（平均偏差约 " + Math.round(avgDev) + " km），建议结合强度差异综合研判。";
        }
        if (avgDev <= 150D) {
            return "路径较为接近（平均偏差约 " + Math.round(avgDev) + " km），强度差异是主要变量。";
        }
        return "路径差异较大（平均偏差约 " + Math.round(avgDev) + " km），不宜直接套用对方的影响范围。";
    }

    /**
     * 计算两条轨迹的双向平均最近点距离。
     *
     * @param a 台风A
     * @param b 台风B
     * @return 平均偏差（km）；任一方无轨迹时返回 0
     */
    private double pathDeviationKm(Typhoon a, Typhoon b) {
        List<double[]> pa = samplePoints(a.getTyphoonId().longValue());
        List<double[]> pb = samplePoints(b.getTyphoonId().longValue());
        if (pa.isEmpty() || pb.isEmpty()) {
            return 0D;
        }
        double ab = meanNearest(pa, pb);
        double ba = meanNearest(pb, pa);
        return (ab + ba) / 2D;
    }

    /**
     * 抽样轨迹点。
     *
     * <p>按索引等间隔抽取而不是按时间：按时间抽会在慢速段点特别密，
     * 让那段路径在平均距离里获得过高权重。
     *
     * @param typhoonId 台风ID
     * @return {@code [lon, lat]} 列表
     */
    private List<double[]> samplePoints(long typhoonId) {
        List<TyphoonTrack> tracks = trackService.listEntities(typhoonId);
        List<double[]> pts = new ArrayList<double[]>();
        for (TyphoonTrack t : tracks) {
            if (t.getLatitude() != null && t.getLongitude() != null) {
                pts.add(new double[]{t.getLongitude().doubleValue(), t.getLatitude().doubleValue()});
            }
        }
        if (pts.size() <= MAX_SAMPLE_POINTS) {
            return pts;
        }
        double step = (double) pts.size() / MAX_SAMPLE_POINTS;
        List<double[]> sampled = new ArrayList<double[]>(MAX_SAMPLE_POINTS);
        for (int i = 0; i < MAX_SAMPLE_POINTS; i++) {
            sampled.add(pts.get((int) Math.floor(i * step)));
        }
        return sampled;
    }

    /**
     * 对 from 中每个点求到 to 的最近距离并取平均。
     *
     * @param from 源点集
     * @param to   目标点集
     * @return 平均最近距离（km）
     */
    private double meanNearest(List<double[]> from, List<double[]> to) {
        double sum = 0D;
        for (double[] p : from) {
            double best = Double.MAX_VALUE;
            for (double[] q : to) {
                double d = GeoUtil.distanceKm(p[1], p[0], q[1], q[0]);
                if (d < best) {
                    best = d;
                }
            }
            sum += best == Double.MAX_VALUE ? 0D : best;
        }
        return from.isEmpty() ? 0D : sum / from.size();
    }

    /**
     * 取明细里的平均强度差。
     *
     * @param details 明细数组
     * @return 平均强度差（取整）
     */
    private int intensityDiffAvg(ArrayNode details) {
        if (details == null || details.size() == 0) {
            return 0;
        }
        int sum = 0;
        for (int i = 0; i < details.size(); i++) {
            sum += details.get(i).path("intensity_diff").asInt(0);
        }
        return Math.round((float) sum / details.size());
    }

    /**
     * 峰值风力等级。
     *
     * @param typhoon 台风
     * @return 等级；无值返回 0
     */
    private int force(Typhoon typhoon) {
        return typhoon.getMaxWindForce() == null ? 0 : typhoon.getMaxWindForce().intValue();
    }

    /**
     * 台风显示名。
     *
     * @param typhoon 台风
     * @return 中文名优先，其次国际编号
     */
    private String name(Typhoon typhoon) {
        if (typhoon.getChineseName() != null && !typhoon.getChineseName().trim().isEmpty()) {
            return typhoon.getChineseName().trim();
        }
        return typhoon.getInternationalId() == null ? ("#" + typhoon.getTyphoonId()) : typhoon.getInternationalId();
    }

    /**
     * 生成默认对比名称。
     *
     * @param provided 用户提供的名称
     * @param primary  主台风
     * @param others   对比台风
     * @return 名称
     */
    private String resolveName(String provided, Typhoon primary, List<Typhoon> others) {
        if (provided != null && !provided.trim().isEmpty()) {
            String n = provided.trim();
            return n.length() > NAME_MAX ? n.substring(0, NAME_MAX) : n;
        }
        StringBuilder sb = new StringBuilder(name(primary));
        sb.append(" vs ");
        for (int i = 0; i < others.size(); i++) {
            if (i > 0) {
                sb.append('、');
            }
            sb.append(name(others.get(i)));
        }
        sb.append(" 路径对比");
        return sb.length() > NAME_MAX ? sb.substring(0, NAME_MAX) : sb.toString();
    }

    /**
     * ID 集合转 JSON 数组。
     *
     * @param ids ID 集合
     * @return 数组节点
     */
    private JsonNode idArray(Set<Long> ids) {
        ArrayNode arr = JsonUtil.mapper().createArrayNode();
        for (Long id : ids) {
            arr.add(id.longValue());
        }
        return arr;
    }
}
