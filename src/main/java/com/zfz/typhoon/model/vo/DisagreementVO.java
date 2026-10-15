package com.zfz.typhoon.model.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 预报分歧解析视图对象（面向前端 P3 分歧解析页）。
 *
 * <p>对应项目描述文档「核心特色功能一：多机构集合预报分歧挖掘与不确定性解析引擎」。
 * 本对象<u>不只是把各家路径叠起来画线</u>，而是给出量化结论：
 * <ol>
 *   <li>{@link #dimensions} —— 登陆地点 / 登陆时间 / 峰值强度 / 移动速度
 *       四个维度的离散范围与方差；</li>
 *   <li>{@link #index} —— 归一化分歧指数（0—1），{@link #highUncertainty} 超过
 *       阈值（默认 0.70）时置 true，页面显著标注；</li>
 *   <li>{@link #convergence} —— 与上一次分析相比分歧是"扩大中 / 收敛中 / 持平"，
 *       对应"时间轴联动，观察分歧是收敛还是扩大"的要求；</li>
 *   <li>{@link #conclusion} —— 面向公众的自然语言结论，告诉用户
 *       "当前预报可信度较低，需持续跟踪更新"，而不是让用户自己看五颜六色的线。</li>
 * </ol>
 *
 * @author 追风者项目组
 */
public class DisagreementVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 风险提示：分歧过大时页面的固定提示 */
    public static final String DISCLAIMER =
            "预报分歧解析仅为量化分析结果，不能作为判断台风发展走向的唯一依据，"
                    + "请以气象部门官方发布为准。";

    /** 分歧风险等级：低 */
    public static final String RISK_LOW = "低";
    /** 分歧风险等级：中 */
    public static final String RISK_MEDIUM = "中";
    /** 分歧风险等级：高 */
    public static final String RISK_HIGH = "高";

    /** 台风ID */
    private Long typhoonId;

    /** 台风名称 */
    private String typhoonName;

    /** 分歧风险等级：低 / 中 / 高 */
    private String riskLevel;

    /** 分歧指数（0—1） */
    private Double index;

    /** 是否高不确定性 */
    private Boolean highUncertainty;

    /** 分歧趋势：扩大中 / 收敛中 / 持平 */
    private String convergence;

    /** 维度明细 */
    private List<DimensionItem> dimensions = new ArrayList<DimensionItem>();

    /** 面向公众的结论文案 */
    private String conclusion;

    /** 参与分析的机构数 */
    private Integer agencyCount;

    /** 参与分析的机构代码，如 CMA / JMA / JTWC */
    private List<String> agencies = new ArrayList<String>();

    /** 各机构预报登陆点经纬度，供前端绘制概率登陆分布 */
    private List<List<Double>> landfallPoints = new ArrayList<List<Double>>();

    /** 分析时间（yyyy-MM-dd HH:mm） */
    private String analysisTime;

    /** 免责提示 */
    private String disclaimer = DISCLAIMER;

    public DisagreementVO() {
    }

    /**
     * 单个分歧维度。
     */
    public static class DimensionItem implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 维度名称，如「登陆地点」 */
        private String name;

        /** 离散范围描述，如「118.09°E ~ 116.68°E」 */
        private String spread;

        /** 归一化离散度（0—1）；与 {@link DisagreementVO#index} 同量纲，前端展示百分比需 ×100 */
        private Double variance;

        /** 极差（原始单位，如 km） */
        private Double range;

        /** 一致性评价文案 */
        private String comment;

        public DimensionItem() {
        }

        /**
         * 构造维度明细。
         *
         * @param name     维度名称
         * @param spread   离散范围描述
         * @param variance 归一化离散度
         */
        public DimensionItem(String name, String spread, Double variance) {
            this.name = name;
            this.spread = spread;
            this.variance = variance;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSpread() {
            return spread;
        }

        public void setSpread(String spread) {
            this.spread = spread;
        }

        public Double getVariance() {
            return variance;
        }

        public void setVariance(Double variance) {
            this.variance = variance;
        }

        public Double getRange() {
            return range;
        }

        public void setRange(Double range) {
            this.range = range;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }
    }

    public Long getTyphoonId() {
        return typhoonId;
    }

    public void setTyphoonId(Long typhoonId) {
        this.typhoonId = typhoonId;
    }

    public String getTyphoonName() {
        return typhoonName;
    }

    public void setTyphoonName(String typhoonName) {
        this.typhoonName = typhoonName;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Double getIndex() {
        return index;
    }

    public void setIndex(Double index) {
        this.index = index;
    }

    public Boolean getHighUncertainty() {
        return highUncertainty;
    }

    public void setHighUncertainty(Boolean highUncertainty) {
        this.highUncertainty = highUncertainty;
    }

    public String getConvergence() {
        return convergence;
    }

    public void setConvergence(String convergence) {
        this.convergence = convergence;
    }

    public List<DimensionItem> getDimensions() {
        return dimensions;
    }

    public void setDimensions(List<DimensionItem> dimensions) {
        this.dimensions = dimensions;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public Integer getAgencyCount() {
        return agencyCount;
    }

    public void setAgencyCount(Integer agencyCount) {
        this.agencyCount = agencyCount;
    }

    public List<String> getAgencies() {
        return agencies;
    }

    public void setAgencies(List<String> agencies) {
        this.agencies = agencies;
    }

    public List<List<Double>> getLandfallPoints() {
        return landfallPoints;
    }

    public void setLandfallPoints(List<List<Double>> landfallPoints) {
        this.landfallPoints = landfallPoints;
    }

    public String getAnalysisTime() {
        return analysisTime;
    }

    public void setAnalysisTime(String analysisTime) {
        this.analysisTime = analysisTime;
    }

    public String getDisclaimer() {
        return disclaimer;
    }

    public void setDisclaimer(String disclaimer) {
        this.disclaimer = disclaimer;
    }
}
