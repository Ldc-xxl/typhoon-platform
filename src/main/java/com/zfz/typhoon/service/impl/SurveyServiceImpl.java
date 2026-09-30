package com.zfz.typhoon.service.impl;

import com.zfz.typhoon.dao.SurveyAggResultDao;
import com.zfz.typhoon.dao.UserBehaviorSurveyDao;
import com.zfz.typhoon.dao.impl.SurveyAggResultDaoImpl;
import com.zfz.typhoon.dao.impl.UserBehaviorSurveyDaoImpl;
import com.zfz.typhoon.model.City;
import com.zfz.typhoon.model.SurveyAggResult;
import com.zfz.typhoon.model.Typhoon;
import com.zfz.typhoon.model.UserBehaviorSurvey;
import com.zfz.typhoon.model.vo.SurveyAggResultVO;
import com.zfz.typhoon.service.CityService;
import com.zfz.typhoon.service.SurveyService;
import com.zfz.typhoon.service.TyphoonService;
import com.zfz.typhoon.util.BusinessException;
import com.zfz.typhoon.util.DateUtil;
import com.zfz.typhoon.util.ParamUtil;
import com.zfz.typhoon.util.PasswordUtil;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 防灾行为问卷业务实现。
 *
 * <h3>量纲约定（重要）</h3>
 * 数据库 {@code survey_agg_results} 的各比率列注释即为「%」，
 * 聚合 SQL 里也用 {@code 100.0 * ...} 算出 0—100 的数。
 * 因此本实现<b>保持 0—100 原样输出</b>，不做任何除法：
 * <ul>
 *   <li>零转换 → 少一处出错的地方；</li>
 *   <li>前端 {@code survey.js} 的示例值本身就是 {@code 66.5} + {@code '%'}，
 *       语义与量纲天然对齐。</li>
 * </ul>
 *
 * <h3>反向科普提示（{@link #buildHint}）的生成原则</h3>
 * 提示必须<b>由数据驱动</b>，不是写死的文案。各条规则都遵循同一个句法：
 * 「多数人担心 X，但只有少数人做了 Y」——把"认知"与"行为"的落差指出来，
 * 这才是这个模块存在的意义（发现认知-行为鸿沟，而不是单纯统计）。
 * 若数据没体现出落差，就如实说"认知与行为基本匹配"，不硬凑一条提示。
 *
 * @author 追风者项目组
 * @see SurveyService
 */
public class SurveyServiceImpl implements SurveyService {

    /** 当前问卷版本（随题目调整递增，便于日后按版本切分数据） */
    private static final String QUESTION_SET_VERSION = "v1.0";

    /** 可统计的最小样本量；低于此值仍返回数据，但标记样本不足 */
    private static final int MIN_SAMPLES = 30;

    /** 认定"多数人"的比率阈值（%） */
    private static final double MAJORITY_RATE = 60D;

    /** 认定"少数人"的比率阈值（%） */
    private static final double MINORITY_RATE = 40D;

    /** 认定"认知-行为落差"需要的最小差值（百分点） */
    private static final double GAP_POINTS = 20D;

    /** 城市卡片最多返回多少条（按样本量降序），避免看板过长 */
    private static final int MAX_CITY_CARDS = 12;

    private final UserBehaviorSurveyDao surveyDao;
    private final SurveyAggResultDao aggDao;
    private final TyphoonService typhoonService;
    private final CityService cityService;

    /**
     * 生产用构造：按默认实现装配依赖。
     * <p>之所以把它拆成"无参 + 带参"两个构造，是为了给单元测试留出注入替身的缝隙：
     * 依赖全部在字段初始化里 new 出来的话，测试就必须连上真实 MySQL 才能跑，
     * 单元测试也就退化成了集成测试。
     */
    public SurveyServiceImpl() {
        this(new UserBehaviorSurveyDaoImpl(), new SurveyAggResultDaoImpl(), new TyphoonServiceImpl(), new CityServiceImpl());
    }

    /**
     * 测试用构造：注入替身依赖（Mockito 生成的 mock 或手写桩）。
     * <p>参数顺序与上方字段声明一致，均为本服务运行所需的协作者。
     */
    public SurveyServiceImpl(UserBehaviorSurveyDao surveyDao, SurveyAggResultDao aggDao, TyphoonService typhoonService, CityService cityService) {
        this.surveyDao = surveyDao;
        this.aggDao = aggDao;
        this.typhoonService = typhoonService;
        this.cityService = cityService;
    }

    @Override
    public long submit(UserBehaviorSurvey survey) {
        if (survey == null) {
            throw BusinessException.badRequest("问卷内容不能为空");
        }
        long typhoonId = positiveId(survey.getTyphoonId(), "typhoonId");
        long cityId = positiveId(survey.getCityId(), "cityId");
        survey.setTyphoonId(Long.valueOf(typhoonId));
        survey.setCityId(Long.valueOf(cityId));

        // 台风必须存在，否则统计时无法挂靠（把外键异常转成友好提示）
        typhoonService.requireTyphoon(typhoonId);

        if (survey.getPreparedSupplies() == null) {
            throw BusinessException.badRequest("q1「是否储备物资」为必答题");
        }
        if (survey.getPayAttentionEvacuation() == null) {
            throw BusinessException.badRequest("q2「是否关注撤离信息」为必答题");
        }

        String concern = survey.getWorriedDisasterType();
        if (concern == null || concern.trim().isEmpty()) {
            throw BusinessException.badRequest("q3「最担心的灾害类型」为必答题");
        }
        concern = concern.trim().toUpperCase();
        if (!Arrays.asList(UserBehaviorSurvey.CONCERN_WIND, UserBehaviorSurvey.CONCERN_RAIN,
                UserBehaviorSurvey.CONCERN_STORM_SURGE, UserBehaviorSurvey.CONCERN_OTHER).contains(concern)) {
            throw BusinessException.badRequest("q3 取值非法，应为 WIND / RAIN / STORM_SURGE / OTHER 之一");
        }
        survey.setWorriedDisasterType(concern);

        if (survey.getTrustLevel() == null) {
            throw BusinessException.badRequest("q4「对预报的信任程度」为必答题");
        }
        ParamUtil.checkRange(survey.getTrustLevel(), "trustLevel", 1, 5);

        survey.setQuestionSetVersion(QUESTION_SET_VERSION);
        survey.setSubmitTime(DateUtil.now());
        // 联系方式选填；填了就脱敏存储，绝不原样落库
        survey.setContactInfo(PasswordUtil.maskContact(survey.getContactInfo()));

        return surveyDao.insert(survey);
    }

    @Override
    public SurveyAggResultVO agg(long typhoonId, Long cityId) {
        Typhoon typhoon = typhoonService.requireTyphoon(typhoonId);
        SurveyAggResultVO vo = new SurveyAggResultVO();
        vo.setTyphoonName(typhoon.getChineseName());

        if (cityId != null) {
            return aggOneCity(vo, typhoonId, cityId.longValue());
        }

        // 全台风视图：DAO 已按城市分组，需自行加权汇总
        List<SurveyAggResult> rows = surveyDao.aggregateByTyphoon(typhoonId);
        if (rows.isEmpty()) {
            vo.setSampleSize(Integer.valueOf(0));
            vo.setEnoughSamples(Boolean.FALSE);
            vo.setHint("该台风暂无问卷样本，欢迎参与调研。");
            return vo;
        }

        SurveyAggResult total = weightedTotal(rows);
        fillFromEntity(vo, total);

        int shown = 0;
        for (SurveyAggResult row : rows) {
            if (shown >= MAX_CITY_CARDS) {
                break;
            }
            vo.getCityBreakdown().add(cityAgg(row));
            shown++;
        }
        vo.setHint(buildHint(rateMap(total)));
        return vo;
    }

    @Override
    public int rebuild(long typhoonId) {
        Typhoon typhoon = typhoonService.requireTyphoon(typhoonId);
        List<SurveyAggResult> rows = surveyDao.aggregateByTyphoon(typhoonId);
        if (rows.isEmpty()) {
            aggDao.deleteByTyphoon(typhoonId);
            return 0;
        }
        LocalDateTime statTime = DateUtil.now();
        for (SurveyAggResult row : rows) {
            row.setTyphoonId(Long.valueOf(typhoonId));
            row.setStatTime(statTime);
            row.setTyphoonName(typhoon.getChineseName());
            City city = row.getCityId() == null ? null : cityService.getById(row.getCityId().longValue());
            if (city != null) {
                row.setCityName(city.getCityName());
                row.setProvince(city.getProvince());
            }
        }
        return aggDao.upsertAll(rows);
    }

    @Override
    public int countSamples(long typhoonId) {
        return surveyDao.countByTyphoon(typhoonId);
    }

    /**
     * 校验并取出必填的正整数 ID。
     *
     * <p>Service 层拿到的是已经反序列化好的实体，不是 HttpServletRequest，
     * 所以这里不复用 {@code ParamUtil} 的请求参数版本，只复用它的校验语义。
     *
     * @param value 字段值
     * @param name  字段名（写进错误提示）
     * @return 正整数
     */
    private long positiveId(Long value, String name) {
        if (value == null || value.longValue() <= 0L) {
            throw BusinessException.badRequest(name + " 不能为空且必须为正整数");
        }
        return value.longValue();
    }

    /* ==================================================================== */
    /*  内部：单城市视图                                                      */
    /* ==================================================================== */

    /**
     * 组装单城市聚合视图。
     *
     * <p>该城市尚无样本时返回空样本视图而不是抛 404 ——
     * 对看板来说"还没有数据"是正常业务状态，前端要展示占位提示。
     *
     * @param vo        目标视图
     * @param typhoonId 台风ID
     * @param cityId    城市ID
     * @return 视图
     */
    private SurveyAggResultVO aggOneCity(SurveyAggResultVO vo, long typhoonId, long cityId) {
        SurveyAggResult one = surveyDao.aggregateByTyphoonAndCity(typhoonId, cityId);
        if (one == null || one.getTotalCount() == null || one.getTotalCount().intValue() <= 0) {
            vo.setSampleSize(Integer.valueOf(0));
            vo.setEnoughSamples(Boolean.FALSE);
            vo.setHint("该城市暂无问卷样本，欢迎参与调研。");
            return vo;
        }
        // 聚合 SQL 只回 city_id，城市名从缓存补（避免在聚合里再 JOIN 一次）
        City city = cityService.getById(cityId);
        if (city != null) {
            one.setCityId(Long.valueOf(cityId));
            one.setCityName(city.getCityName());
            one.setProvince(city.getProvince());
        }
        fillFromEntity(vo, one);
        vo.getCityBreakdown().add(cityAgg(one));
        vo.setHint(buildHint(rateMap(one)));
        return vo;
    }

    /* ==================================================================== */
    /*  内部：加权汇总与映射                                                  */
    /* ==================================================================== */

    /**
     * 把「按城市分组的多行聚合」加权合并成一行总体结果。
     *
     * <p>率值必须按<b>样本量加权</b>而不是简单平均：某城市 5 份样本算出 100%，
     * 另一个城市 500 份样本算出 40%，简单平均会把结果拉到 70%，严重失真。
     *
     * @param rows 分城市聚合行
     * @return 合并后的一行
     */
    private SurveyAggResult weightedTotal(List<SurveyAggResult> rows) {
        int totalCount = 0;
        for (SurveyAggResult r : rows) {
            totalCount += r.getTotalCount() == null ? 0 : r.getTotalCount().intValue();
        }
        SurveyAggResult out = new SurveyAggResult();
        out.setTotalCount(Integer.valueOf(totalCount));
        if (totalCount <= 0) {
            out.setSupplyPrepRate(Double.valueOf(0D));
            out.setEvacuationAttentionRate(Double.valueOf(0D));
            out.setHeavyRainWorryRate(Double.valueOf(0D));
            out.setStormSurgeWorryRate(Double.valueOf(0D));
            out.setHighTrustRate(Double.valueOf(0D));
            out.setRiskOverestimationRate(Double.valueOf(0D));
            out.setRiskUnderestimationRate(Double.valueOf(0D));
            return out;
        }
        out.setSupplyPrepRate(weighted(rows, Dim.SUPPLY, totalCount));
        out.setEvacuationAttentionRate(weighted(rows, Dim.EVACUATION, totalCount));
        out.setHeavyRainWorryRate(weighted(rows, Dim.RAIN, totalCount));
        out.setStormSurgeWorryRate(weighted(rows, Dim.SURGE, totalCount));
        out.setHighTrustRate(weighted(rows, Dim.HIGH_TRUST, totalCount));
        out.setRiskOverestimationRate(weighted(rows, Dim.OVER, totalCount));
        out.setRiskUnderestimationRate(weighted(rows, Dim.UNDER, totalCount));
        return out;
    }

    /** 加权维度枚举：避免写 7 段重复的循环体 */
    private enum Dim {
        SUPPLY, EVACUATION, RAIN, SURGE, HIGH_TRUST, OVER, UNDER
    }

    /**
     * 按样本量加权求某个维度的总体比率。
     *
     * @param rows       分城市聚合行
     * @param dim        维度
     * @param totalCount 总样本量
     * @return 加权比率（0—100）
     */
    private Double weighted(List<SurveyAggResult> rows, Dim dim, int totalCount) {
        double acc = 0D;
        for (SurveyAggResult r : rows) {
            int c = r.getTotalCount() == null ? 0 : r.getTotalCount().intValue();
            Double rate = rateOf(r, dim);
            if (c <= 0 || rate == null) {
                continue;
            }
            acc += rate.doubleValue() * c;
        }
        return Double.valueOf(round2(acc / totalCount));
    }

    /**
     * 读某个维度的率值。
     *
     * @param r   聚合行
     * @param dim 维度
     * @return 率值（0—100）；缺失返回 null
     */
    private Double rateOf(SurveyAggResult r, Dim dim) {
        switch (dim) {
            case SUPPLY:
                return r.getSupplyPrepRate();
            case EVACUATION:
                return r.getEvacuationAttentionRate();
            case RAIN:
                return r.getHeavyRainWorryRate();
            case SURGE:
                return r.getStormSurgeWorryRate();
            case HIGH_TRUST:
                return r.getHighTrustRate();
            case OVER:
                return r.getRiskOverestimationRate();
            default:
                return r.getRiskUnderestimationRate();
        }
    }

    /**
     * 把聚合实体填进视图。
     *
     * @param vo 目标视图
     * @param r  聚合实体
     */
    private void fillFromEntity(SurveyAggResultVO vo, SurveyAggResult r) {
        int count = r.getTotalCount() == null ? 0 : r.getTotalCount().intValue();
        vo.setSampleSize(Integer.valueOf(count));
        vo.setSuppliesRate(zero(r.getSupplyPrepRate()));
        vo.setEvacuationRate(zero(r.getEvacuationAttentionRate()));
        vo.setHighTrustRate(zero(r.getHighTrustRate()));
        vo.setOverestimationRate(zero(r.getRiskOverestimationRate()));
        vo.setUnderestimationRate(zero(r.getRiskUnderestimationRate()));

        // DAO 只单独统计「暴雨」「风暴潮」两档，大风由 100 − 两者 反推，
        // 避免为此新增两次全表扫描。
        double rain = v(r.getHeavyRainWorryRate());
        double surge = v(r.getStormSurgeWorryRate());
        Map<String, Double> concern = new LinkedHashMap<String, Double>();
        concern.put("大风", Double.valueOf(Math.max(0D, round2(100D - rain - surge))));
        concern.put("暴雨", Double.valueOf(rain));
        concern.put("风暴潮", Double.valueOf(surge));
        vo.setConcernDist(concern);

        vo.setEnoughSamples(Boolean.valueOf(count >= MIN_SAMPLES));
    }

    /**
     * 聚合实体 → 单城市视图。
     *
     * @param r 聚合实体
     * @return 城市聚合视图
     */
    private SurveyAggResultVO.CityAgg cityAgg(SurveyAggResult r) {
        SurveyAggResultVO.CityAgg agg = new SurveyAggResultVO.CityAgg();
        agg.setCityId(r.getCityId());
        agg.setCityName(r.getCityName());
        agg.setProvince(r.getProvince());
        agg.setSampleSize(r.getTotalCount());
        agg.setSuppliesRate(zero(r.getSupplyPrepRate()));
        agg.setEvacuationRate(zero(r.getEvacuationAttentionRate()));
        agg.setRainWorryRate(zero(r.getHeavyRainWorryRate()));
        agg.setStormSurgeWorryRate(zero(r.getStormSurgeWorryRate()));
        agg.setUnderestimationRate(zero(r.getRiskUnderestimationRate()));
        return agg;
    }

    /**
     * 抽出用于生成提示的率值表。
     *
     * @param r 聚合实体
     * @return 键为语义名、值为 0—100 的率值
     */
    private Map<String, Double> rateMap(SurveyAggResult r) {
        Map<String, Double> m = new LinkedHashMap<String, Double>();
        m.put("supply", zero(r.getSupplyPrepRate()));
        m.put("evacuation", zero(r.getEvacuationAttentionRate()));
        m.put("rain", zero(r.getHeavyRainWorryRate()));
        m.put("surge", zero(r.getStormSurgeWorryRate()));
        m.put("trust", zero(r.getHighTrustRate()));
        m.put("over", zero(r.getRiskOverestimationRate()));
        m.put("under", zero(r.getRiskUnderestimationRate()));
        return m;
    }

    /* ==================================================================== */
    /*  内部：反向科普提示                                                    */
    /* ==================================================================== */

    /**
     * 由群体数据反向生成科普提示。
     *
     * <p>四条候选规则按"落差严重程度"排序，取第一条命中的：
     * <ol>
     *   <li>担心暴雨内涝 ≫ 储备物资 —— 最典型也最常见的鸿沟；</li>
     *   <li>担心风暴潮 ≫ 关注撤离信息 —— 沿海地区的致命盲区；</li>
     *   <li>信任度低且判断偏差大 —— 说明对预报不确定性的解读能力不足；</li>
     *   <li>物资储备率偏低 —— 整体准备度问题。</li>
     * </ol>
     * 都没命中时返回"认知与行为基本匹配"，不编造提示。
     *
     * @param rate 率值表（0—100）
     * @return 提示文案
     */
    private String buildHint(Map<String, Double> rate) {
        double supply = v(rate.get("supply"));
        double evacuation = v(rate.get("evacuation"));
        double rain = v(rate.get("rain"));
        double surge = v(rate.get("surge"));
        double trust = v(rate.get("trust"));
        double over = v(rate.get("over"));
        double under = v(rate.get("under"));

        if (rain >= MAJORITY_RATE && supply <= MINORITY_RATE && rain - supply >= GAP_POINTS) {
            return pct(rain) + " 的用户最担心暴雨内涝，但仅 " + pct(supply)
                    + " 提前储备了应急物资 —— 建议在台风预警期同步推送「家庭应急包清单」。";
        }
        if (surge >= MAJORITY_RATE && evacuation <= MINORITY_RATE && surge - evacuation >= GAP_POINTS) {
            return pct(surge) + " 的用户担心风暴潮，但只有 " + pct(evacuation)
                    + " 主动关注撤离路线 —— 沿海地区应在登陆前 24 小时推送撤离点位图。";
        }
        if (trust <= MINORITY_RATE && over + under >= MAJORITY_RATE) {
            return "仅 " + pct(trust) + " 的用户认为预报高度可信，且 "
                    + pct(over + under) + " 的用户判断与预报偏差较大 —— "
                    + "建议加强「预报为何存在不确定性」的科普解读。";
        }
        if (supply <= MINORITY_RATE) {
            return "物资储备率仅 " + pct(supply) + "，整体防灾准备度偏低 —— "
                    + "建议在台风季来临前开展一轮面向社区的物资准备提醒。";
        }
        return "当前样本显示，公众的风险认知与防灾行为基本匹配（物资储备率 "
                + pct(supply) + "，撤离信息关注率 " + pct(evacuation) + "）。";
    }

    /**
     * 数值转带一位小数的百分比文本。
     *
     * @param value 0—100 的数值
     * @return 形如 {@code 66.5%}
     */
    private String pct(double value) {
        return String.format("%.1f%%", Double.valueOf(value));
    }

    /**
     * 空值兜底为 0，避免前端拿到 null 后渲染出 "NaN%"。
     *
     * @param d 原值
     * @return 非空值
     */
    private Double zero(Double d) {
        return Double.valueOf(v(d));
    }

    /**
     * 取 double 值，null 视为 0。
     *
     * @param d 原值
     * @return 数值
     */
    private double v(Double d) {
        return d == null ? 0D : d.doubleValue();
    }

    /**
     * 保留两位小数。
     *
     * @param d 原值
     * @return 两位小数
     */
    private double round2(double d) {
        return Math.round(d * 100D) / 100D;
    }
}
