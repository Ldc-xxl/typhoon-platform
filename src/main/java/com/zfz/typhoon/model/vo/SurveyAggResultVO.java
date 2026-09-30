package com.zfz.typhoon.model.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 问卷聚合统计视图对象（面向前端 P7 防灾行为调研看板）。
 *
 * <p>对应项目描述文档「核心特色功能四」。
 *
 * <p><b>隐私红线</b>：本对象是问卷数据<b>唯一</b>对外的形态，只含群体统计，
 * 绝不包含任何个人记录。联系人信息仅用于回访，不在此暴露。
 *
 * <p><b>量纲</b>：各比率均为 <b>0—100</b> 的百分数（如 {@code 66.5} 表示 66.5%），
 * 与数据库 {@code survey_agg_results} 各列「百分比」的语义保持一致，
 * 避免后端做一次除法再让前端乘回来。前端 {@code survey.js} 直接拼 {@code '%'} 即可。
 * 本 VO 的 {@link #hint} 是平台据群体反馈<b>反向生成</b>的科普提示
 * （如「70% 用户关注暴雨内涝，但仅 30% 做了物资储备」）。
 *
 * @author 追风者项目组
 */
public class SurveyAggResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 隐私说明，随聚合结果一起返回 */
    public static final String PRIVACY_NOTE =
            "以下为群体聚合统计，不含任何个人信息；联系方式仅用于问卷回访与科普服务，不对外公开。";

    /** 有效样本量 */
    private Integer sampleSize;

    /** 物资储备率（0—100） */
    private Double suppliesRate;

    /** 撤离信息关注率（0—100） */
    private Double evacuationRate;

    /** 最担心灾害的分布：大风 / 暴雨 / 风暴潮（值域 0—100） */
    private Map<String, Double> concernDist = new LinkedHashMap<String, Double>();

    /** 对预报高可信比例（0—100） */
    private Double highTrustRate;

    /** 高估风险比例（0—100） */
    private Double overestimationRate;

    /** 低估风险比例（0—100） */
    private Double underestimationRate;

    /** 反向科普提示 */
    private String hint;

    /** 分城市明细 */
    private List<CityAgg> cityBreakdown = new ArrayList<CityAgg>();

    /** 关联台风名称 */
    private String typhoonName;

    /** 样本量是否达到可统计门槛 */
    private Boolean enoughSamples;

    /** 隐私说明 */
    private String privacyNote = PRIVACY_NOTE;

    public SurveyAggResultVO() {
    }

    /**
     * 单个城市的聚合结果。
     */
    public static class CityAgg implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 城市ID */
        private Long cityId;

        /** 城市名称 */
        private String cityName;

        /** 省份 */
        private String province;

        /** 样本量 */
        private Integer sampleSize;

        /** 物资储备率（0—100） */
        private Double suppliesRate;

        /** 撤离信息关注率（0—100） */
        private Double evacuationRate;

        /** 担心暴雨内涝比例（0—100） */
        private Double rainWorryRate;

        /** 担心风暴潮比例（0—100） */
        private Double stormSurgeWorryRate;

        /** 低估风险比例（0—100） */
        private Double underestimationRate;

        public CityAgg() {
        }

        public Long getCityId() {
            return cityId;
        }

        public void setCityId(Long cityId) {
            this.cityId = cityId;
        }

        public String getCityName() {
            return cityName;
        }

        public void setCityName(String cityName) {
            this.cityName = cityName;
        }

        public String getProvince() {
            return province;
        }

        public void setProvince(String province) {
            this.province = province;
        }

        public Integer getSampleSize() {
            return sampleSize;
        }

        public void setSampleSize(Integer sampleSize) {
            this.sampleSize = sampleSize;
        }

        public Double getSuppliesRate() {
            return suppliesRate;
        }

        public void setSuppliesRate(Double suppliesRate) {
            this.suppliesRate = suppliesRate;
        }

        public Double getEvacuationRate() {
            return evacuationRate;
        }

        public void setEvacuationRate(Double evacuationRate) {
            this.evacuationRate = evacuationRate;
        }

        public Double getRainWorryRate() {
            return rainWorryRate;
        }

        public void setRainWorryRate(Double rainWorryRate) {
            this.rainWorryRate = rainWorryRate;
        }

        public Double getStormSurgeWorryRate() {
            return stormSurgeWorryRate;
        }

        public void setStormSurgeWorryRate(Double stormSurgeWorryRate) {
            this.stormSurgeWorryRate = stormSurgeWorryRate;
        }

        public Double getUnderestimationRate() {
            return underestimationRate;
        }

        public void setUnderestimationRate(Double underestimationRate) {
            this.underestimationRate = underestimationRate;
        }
    }

    public Integer getSampleSize() {
        return sampleSize;
    }

    public void setSampleSize(Integer sampleSize) {
        this.sampleSize = sampleSize;
    }

    public Double getSuppliesRate() {
        return suppliesRate;
    }

    public void setSuppliesRate(Double suppliesRate) {
        this.suppliesRate = suppliesRate;
    }

    public Double getEvacuationRate() {
        return evacuationRate;
    }

    public void setEvacuationRate(Double evacuationRate) {
        this.evacuationRate = evacuationRate;
    }

    public Map<String, Double> getConcernDist() {
        return concernDist;
    }

    public void setConcernDist(Map<String, Double> concernDist) {
        this.concernDist = concernDist;
    }

    public Double getHighTrustRate() {
        return highTrustRate;
    }

    public void setHighTrustRate(Double highTrustRate) {
        this.highTrustRate = highTrustRate;
    }

    public Double getOverestimationRate() {
        return overestimationRate;
    }

    public void setOverestimationRate(Double overestimationRate) {
        this.overestimationRate = overestimationRate;
    }

    public Double getUnderestimationRate() {
        return underestimationRate;
    }

    public void setUnderestimationRate(Double underestimationRate) {
        this.underestimationRate = underestimationRate;
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    public List<CityAgg> getCityBreakdown() {
        return cityBreakdown;
    }

    public void setCityBreakdown(List<CityAgg> cityBreakdown) {
        this.cityBreakdown = cityBreakdown;
    }

    public String getTyphoonName() {
        return typhoonName;
    }

    public void setTyphoonName(String typhoonName) {
        this.typhoonName = typhoonName;
    }

    public Boolean getEnoughSamples() {
        return enoughSamples;
    }

    public void setEnoughSamples(Boolean enoughSamples) {
        this.enoughSamples = enoughSamples;
    }

    public String getPrivacyNote() {
        return privacyNote;
    }

    public void setPrivacyNote(String privacyNote) {
        this.privacyNote = privacyNote;
    }
}
