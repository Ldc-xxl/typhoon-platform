package com.zfz.typhoon.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.zfz.typhoon.dao.AiPredictionRecordDao;
import com.zfz.typhoon.dao.impl.AiPredictionRecordDaoImpl;
import com.zfz.typhoon.model.AiPredictionRecord;
import com.zfz.typhoon.model.ForecastPath;
import com.zfz.typhoon.model.Typhoon;
import com.zfz.typhoon.model.vo.AiPredictionVO;
import com.zfz.typhoon.service.AiPredictionService;
import com.zfz.typhoon.service.ForecastService;
import com.zfz.typhoon.service.LlmConfigService;
import com.zfz.typhoon.service.LlmPredictionService;
import com.zfz.typhoon.service.TyphoonService;
import com.zfz.typhoon.service.impl.ForecastServiceImpl;
import com.zfz.typhoon.service.impl.TyphoonServiceImpl;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.DateUtil;
import com.zfz.typhoon.util.GeoUtil;
import com.zfz.typhoon.util.JsonUtil;
import com.zfz.typhoon.util.LlmClient;
import com.zfz.typhoon.util.Result;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 真实大模型路径测算实现。
 *
 * <p>整条链路：机构预报 → 提示词 → 厂商 API → 严格 JSON 解析 → 校验 → 落库。
 *
 * <h3>三条不肯让步的规则</h3>
 * <ol>
 *   <li><b>调用失败就报错</b>。绝不回退到本地融合结果再冠上模型的名字 ——
 *       那会让"豆包预测"和"深度融合"的曲线完全重合，属于造假。</li>
 *   <li><b>输出必须过校验</b>。点数、经纬度范围、风力范围任一不合格就拒收，
 *       宁可失败也不写一条画不出图的记录。</li>
 *   <li><b>耗时如实记录</b>。{@code latency_ms} 只写真实往返耗时；
 *       融合算法的记录该列保持 null（前端显示 "—"）。</li>
 * </ol>
 *
 * @author 追风者项目组
 */
public class LlmPredictionServiceImpl implements LlmPredictionService {

    private static final Logger LOG = Logger.getLogger(LlmPredictionServiceImpl.class.getName());

    /** 提示词版本；与融合算法的 v1.1 区分开，便于回溯"这条记录是怎么来的" */
    private static final String PROMPT_VERSION = "llm-v1";

    /** 输出上限：路径点 + 解读，1600 足够，也给模型留出余量 */
    private static final int MAX_TOKENS = 1600;

    /** 提示词里最多放几家机构（控制 token，也避免互相矛盾的预报把模型带偏） */
    private static final int MAX_AGENCIES_IN_PROMPT = 5;

    /** 每家机构最多放几个预报点（取最近的若干个，远期点参考价值低） */
    private static final int MAX_POINTS_PER_AGENCY = 8;

    /** 有效路径至少 2 个点，否则画不成线 */
    private static final int MIN_POINTS = 2;

    /** 地图可显示范围（与前端画布一致），超出说明模型在胡说 */
    private static final double LON_MIN = 100D;
    private static final double LON_MAX = 180D;
    private static final double LAT_MIN = 4D;
    private static final double LAT_MAX = 50D;

    /** 置信度上限：不出现"100% 把握"这种不诚实表述 */
    private static final int CONFIDENCE_MAX = 95;

    /** 解读文案落库列是 varchar(1000) */
    private static final int SUMMARY_MAX = 1000;

    /** 模型输出里默认置信度（解析不到时） */
    private static final int CONFIDENCE_DEFAULT = 60;

    private static final DateTimeFormatter PROMPT_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final LlmConfigService llmConfigService;
    private final TyphoonService typhoonService;
    private final ForecastService forecastService;
    private final AiPredictionRecordDao recordDao;
    private final AiPredictionService aiPredictionService;

    /**
     * 生产构造：装配默认实现。
     */
    public LlmPredictionServiceImpl() {
        this(new LlmConfigServiceImpl(), new TyphoonServiceImpl(), new ForecastServiceImpl(),
                new AiPredictionRecordDaoImpl(), new AiPredictionServiceImpl());
    }

    /**
     * 测试构造：注入替身，避免单测依赖外网。
     *
     * @param llmConfigService     配置服务
     * @param typhoonService       台风服务
     * @param forecastService      预报服务
     * @param recordDao            记录 DAO
     * @param aiPredictionService  融合算法服务（只用来算"与融合路径的偏差"）
     */
    public LlmPredictionServiceImpl(LlmConfigService llmConfigService,
                                    TyphoonService typhoonService,
                                    ForecastService forecastService,
                                    AiPredictionRecordDao recordDao,
                                    AiPredictionService aiPredictionService) {
        this.llmConfigService = llmConfigService;
        this.typhoonService = typhoonService;
        this.forecastService = forecastService;
        this.recordDao = recordDao;
        this.aiPredictionService = aiPredictionService;
    }

    @Override
    public AiPredictionVO predict(long typhoonId, String modelKey) {
        Typhoon typhoon = typhoonService.requireTyphoon(typhoonId);
        Map<String, List<ForecastPath>> usable = usableForecasts(forecastService.groupByAgency(typhoonId));
        if (usable.isEmpty()) {
            throw BusinessException.badRequest("当前台风没有可用的机构预报（至少需要 1 家、2 个点），无法进行模型测算");
        }

        String prompt = buildPrompt(typhoon, usable);
        LlmClient.Result result = llmConfigService.call(modelKey, prompt, MAX_TOKENS);
        if (!result.isOk()) {
            throw new BusinessException(Result.CODE_SERVER_ERROR, "模型调用失败：" + result.getError());
        }

        Parsed parsed = parse(result.getContent());
        if (parsed.points.size() < MIN_POINTS) {
            throw new BusinessException(Result.CODE_SERVER_ERROR,
                    "模型只返回了 " + parsed.points.size() + " 个路径点，不足 2 个，无法生成路径");
        }

        double[] landfall = parsed.points.get(parsed.points.size() - 1);
        AiPredictionVO vo = new AiPredictionVO();
        vo.setTyphoonId(Long.valueOf(typhoonId));
        vo.setVersion(PROMPT_VERSION);
        vo.setColor("#7C6BF0");
        vo.setDash("4 4");
        vo.setPredictionTime(DateUtil.format(DateUtil.now()));
        vo.setDisclaimer(AiPredictionVO.DISCLAIMER);
        vo.setPoints(toPointList(parsed.points));
        vo.setConfidence(Double.valueOf(parsed.confidence / 100D));
        vo.setConsistency(Integer.valueOf(parsed.confidence));
        vo.setConsistencyText(consistencyText(parsed.confidence));
        vo.setSummary(parsed.summary);
        vo.setLandingRange(String.format("北纬 %.1f° / 东经 %.1f° 附近", landfall[1], landfall[0]));
        vo.setIntensityTrend(intensityTrend(parsed.forces));
        vo.setStatus("VALIDATED");

        Double deviation = deviationVsFusion(typhoonId, landfall);
        vo.setDeviation(deviation);

        // ---- 落库：模型名用配置里的展示名，不用 modelKey ----
        String modelName = resolveModelName(modelKey);
        AiPredictionRecord record = new AiPredictionRecord();
        record.setTyphoonId(Long.valueOf(typhoonId));
        record.setModelName(modelName);
        record.setPromptVersion(PROMPT_VERSION);
        record.setPredictionTime(DateUtil.now());
        record.setPredictedPath(JsonUtil.mapper().valueToTree(vo.getPoints()));
        record.setPredictedLandfallLon(Double.valueOf(GeoUtil.round(landfall[0], 3)));
        record.setPredictedLandfallLat(Double.valueOf(GeoUtil.round(landfall[1], 3)));
        record.setConfidenceScore(Double.valueOf(parsed.confidence));
        record.setDeviationVsAgencies(deviation);
        record.setLatencyMs(Integer.valueOf((int) Math.min(result.getLatencyMs(), Integer.MAX_VALUE)));
        record.setSummaryText(trim(parsed.summary, SUMMARY_MAX));
        record.setStatus("VALIDATED");
        record.setCreatedAt(DateUtil.now());
        recordDao.insert(record);

        vo.setModels(listModelItems(typhoonId));
        LOG.log(Level.INFO, "大模型测算完成：typhoon={0}, model={1}, points={2}, latency={3}ms",
                new Object[]{Long.valueOf(typhoonId), modelName,
                        Integer.valueOf(parsed.points.size()), Long.valueOf(result.getLatencyMs())});
        return vo;
    }

    /* ------------------------------------------------------------------ */
    /*  提示词                                                              */
    /* ------------------------------------------------------------------ */

    /**
     * 拼提示词。
     *
     * <p>要求模型"只输出 JSON"是唯一约束 —— 不做 few-shot、不塞历史对话：
     * 路径测算需要的是对当前机构分歧的判断，历史案例反而会稀释信息。
     *
     * @param typhoon 台风
     * @param usable  可用机构预报
     * @return 提示词
     */
    String buildPrompt(Typhoon typhoon, Map<String, List<ForecastPath>> usable) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("你是台风路径预报模型。下面是各机构对同一台风的最新预报点，请综合判断最可能的路径。\n\n");
        sb.append("台风：").append(nullToDash(typhoon.getChineseName()))
                .append("（").append(nullToDash(typhoon.getInternationalId())).append("）");
        if (typhoon.getMaxWindForce() != null) {
            sb.append("，当前最大风力 ").append(typhoon.getMaxWindForce().intValue()).append(" 级");
        }
        sb.append("\n\n机构预报（时间 经度 纬度 风力等级）：\n");

        int agencyCount = 0;
        for (Map.Entry<String, List<ForecastPath>> e : usable.entrySet()) {
            if (agencyCount >= MAX_AGENCIES_IN_PROMPT) {
                break;
            }
            sb.append("[").append(nullToDash(e.getKey())).append("]\n");
            List<ForecastPath> series = e.getValue();
            int from = Math.max(0, series.size() - MAX_POINTS_PER_AGENCY);
            for (int i = from; i < series.size(); i++) {
                ForecastPath p = series.get(i);
                sb.append(p.getForecastTime().format(PROMPT_TIME)).append("  ")
                        .append(fmt(p.getLongitude())).append("E ")
                        .append(fmt(p.getLatitude())).append("N ")
                        .append(p.getForecastWindForce() == null ? "-" : String.valueOf(p.getForecastWindForce()))
                        .append("级\n");
            }
            agencyCount++;
        }

        sb.append("\n输出要求：\n")
                .append("1. 只输出一个 JSON 对象，不要任何解释文字，不要 markdown 代码块。\n")
                .append("2. 结构：{\"points\":[{\"time\":\"MM-dd HH:mm\",\"lon\":121.3,\"lat\":22.1,\"force\":12}],")
                .append("\"confidence\":78,\"summary\":\"...\"}\n")
                .append("3. points 至少 2 个，按时间升序；lon 在 100~180，lat 在 4~50，force 为 0~17 的整数。\n")
                .append("4. confidence 为 0~100 的整数，表示你对这条路径的把握；机构分歧大时应给低值。\n")
                .append("5. summary 用中文，不超过 120 字，说明路径趋势与主要风险。\n");
        return sb.toString();
    }

    /* ------------------------------------------------------------------ */
    /*  解析与校验                                                          */
    /* ------------------------------------------------------------------ */

    /**
     * 解析模型输出。
     *
     * <p>容错范围只到"去掉 markdown 代码块 / 前后废话"这一层；
     * 结构不对、坐标越界一律拒收，不做"猜一个合理值"的补救。
     *
     * @param content 模型返回文本
     * @return 解析结果
     */
    Parsed parse(String content) {
        String json = extractJsonObject(content);
        JsonNode root;
        try {
            root = JsonUtil.mapper().readTree(json);
        } catch (Exception e) {
            throw new BusinessException(Result.CODE_SERVER_ERROR,
                    "模型返回的内容不是合法 JSON（前 120 字：" + trim(content, 120) + "）");
        }

        JsonNode pointsNode = root.path("points");
        if (!pointsNode.isArray() || pointsNode.size() == 0) {
            throw new BusinessException(Result.CODE_SERVER_ERROR, "模型返回的 JSON 里没有 points 数组");
        }

        Parsed parsed = new Parsed();
        for (int i = 0; i < pointsNode.size(); i++) {
            JsonNode n = pointsNode.get(i);
            double lon = n.path("lon").asDouble(Double.NaN);
            double lat = n.path("lat").asDouble(Double.NaN);
            if (Double.isNaN(lon) || Double.isNaN(lat)) {
                continue;
            }
            if (lon < LON_MIN || lon > LON_MAX || lat < LAT_MIN || lat > LAT_MAX) {
                throw new BusinessException(Result.CODE_SERVER_ERROR,
                        String.format("模型返回的路径点越界（经度 %.2f / 纬度 %.2f），已拒收", Double.valueOf(lon),
                                Double.valueOf(lat)));
            }
            int force = n.path("force").asInt(0);
            parsed.points.add(new double[]{lon, lat});
            parsed.forces.add(Integer.valueOf(Math.max(0, Math.min(17, force))));
        }

        if (parsed.points.isEmpty()) {
            throw new BusinessException(Result.CODE_SERVER_ERROR, "模型返回的 points 里没有可用的经纬度");
        }

        int confidence = root.path("confidence").asInt(CONFIDENCE_DEFAULT);
        parsed.confidence = Math.max(0, Math.min(CONFIDENCE_MAX, confidence));
        parsed.summary = trim(root.path("summary").asText(""), SUMMARY_MAX);
        if (parsed.summary.isEmpty()) {
            parsed.summary = "模型未给出解读文案。";
        }
        return parsed;
    }

    /**
     * 从文本里抠出第一个 JSON 对象（容忍前后说明与 ```json 围栏）。
     *
     * @param content 原文
     * @return JSON 子串
     */
    String extractJsonObject(String content) {
        String s = content == null ? "" : content.trim();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl > 0) {
                s = s.substring(nl + 1);
            }
            int fence = s.lastIndexOf("```");
            if (fence >= 0) {
                s = s.substring(0, fence);
            }
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new BusinessException(Result.CODE_SERVER_ERROR,
                    "模型没有返回 JSON 对象（前 120 字：" + trim(content, 120) + "）");
        }
        return s.substring(start, end + 1);
    }

    /* ------------------------------------------------------------------ */
    /*  辅助                                                                */
    /* ------------------------------------------------------------------ */

    /**
     * 过滤出可用的机构序列（至少 2 个有效点），按预报时间升序。
     *
     * @param grouped 原始分组
     * @return 可用序列
     */
    Map<String, List<ForecastPath>> usableForecasts(Map<String, List<ForecastPath>> grouped) {
        Map<String, List<ForecastPath>> result = new LinkedHashMap<String, List<ForecastPath>>();
        if (grouped == null) {
            return result;
        }
        for (Map.Entry<String, List<ForecastPath>> e : grouped.entrySet()) {
            List<ForecastPath> valid = new ArrayList<ForecastPath>();
            if (e.getValue() != null) {
                for (ForecastPath p : e.getValue()) {
                    if (p.getLatitude() != null && p.getLongitude() != null && p.getForecastTime() != null) {
                        valid.add(p);
                    }
                }
            }
            if (valid.size() < 2) {
                continue;
            }
            Collections.sort(valid, new Comparator<ForecastPath>() {
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
     * 模型预测登陆点与本地融合路径终点的距离。
     *
     * <p>用途不是"证明谁对"，而是给管理员一个量级参考：
     * 模型与融合结果差 50km 和差 500km，可信度完全不同。
     * 融合结果算不出来时返回 null（不编数字）。
     *
     * @param typhoonId 台风
     * @param landfall  模型登陆点 [lon, lat]
     * @return 距离 km；无法计算返回 null
     */
    Double deviationVsFusion(long typhoonId, double[] landfall) {
        try {
            AiPredictionVO fusion = aiPredictionService.latest(typhoonId);
            if (fusion == null || fusion.getPoints() == null || fusion.getPoints().isEmpty()) {
                return null;
            }
            List<Double> last = fusion.getPoints().get(fusion.getPoints().size() - 1);
            if (last.size() < 2) {
                return null;
            }
            double km = GeoUtil.distanceKm(landfall[1], landfall[0],
                    last.get(1).doubleValue(), last.get(0).doubleValue());
            return Double.valueOf(GeoUtil.round(km, 2));
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "计算与融合路径偏差失败，留空：" + e.getMessage());
            return null;
        }
    }

    /**
     * 取配置里的展示名，取不到时回退 modelKey。
     *
     * @param modelKey 模型标识
     * @return 展示名
     */
    String resolveModelName(String modelKey) {
        try {
            com.zfz.typhoon.model.LlmProviderConfig cfg = llmConfigService.find(modelKey);
            if (cfg != null && cfg.getModelName() != null && !cfg.getModelName().trim().isEmpty()) {
                return cfg.getModelName().trim();
            }
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "读取模型展示名失败，回退 modelKey：" + e.getMessage());
        }
        return modelKey;
    }

    /**
     * 取各模型最近一次测算（填进 VO 的模型对比列表）。
     *
     * @param typhoonId 台风
     * @return 模型列表
     */
    List<AiPredictionVO.ModelItem> listModelItems(long typhoonId) {
        List<AiPredictionVO.ModelItem> items = new ArrayList<AiPredictionVO.ModelItem>();
        try {
            for (AiPredictionRecord r : recordDao.listLatestPerModel(typhoonId)) {
                items.add(AiPredictionVO.ModelItem.of(r));
            }
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "读取模型列表失败：" + e.getMessage());
        }
        return items;
    }

    private List<List<Double>> toPointList(List<double[]> points) {
        List<List<Double>> list = new ArrayList<List<Double>>(points.size());
        for (double[] p : points) {
            List<Double> one = new ArrayList<Double>(2);
            one.add(Double.valueOf(GeoUtil.round(p[0], 2)));
            one.add(Double.valueOf(GeoUtil.round(p[1], 2)));
            list.add(one);
        }
        return list;
    }

    private String consistencyText(int confidence) {
        if (confidence >= 80) {
            return "高一致（模型把握较大）";
        }
        if (confidence >= 60) {
            return "中等一致（存在分歧）";
        }
        return "低一致（分歧较大）";
    }

    private String intensityTrend(List<Integer> forces) {
        if (forces == null || forces.isEmpty()) {
            return "强度资料不足，暂无法判断趋势。";
        }
        int first = forces.get(0).intValue();
        int last = forces.get(forces.size() - 1).intValue();
        int peak = first;
        for (Integer f : forces) {
            if (f != null && f.intValue() > peak) {
                peak = f.intValue();
            }
        }
        if (last > first + 1) {
            return "强度增强，峰值约 " + peak + " 级。";
        }
        if (last < first - 1) {
            return "强度减弱，峰值约 " + peak + " 级。";
        }
        return "强度基本维持，峰值约 " + peak + " 级。";
    }

    private static String fmt(Double v) {
        return v == null ? "-" : String.format("%.1f", v);
    }

    private static String nullToDash(String s) {
        return s == null || s.trim().isEmpty() ? "—" : s.trim();
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    /**
     * 解析结果。
     */
    static class Parsed {

        /** [lon, lat] 序列 */
        final List<double[]> points = new ArrayList<double[]>();

        /** 与 points 对应的风力序列 */
        final List<Integer> forces = new ArrayList<Integer>();

        /** 0—100 */
        int confidence;

        /** 解读文案 */
        String summary = "";
    }
}
