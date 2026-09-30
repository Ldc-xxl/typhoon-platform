package com.zfz.typhoon.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 调研聚合统计实体（对应表 {@code survey_agg_results}）。
 *
 * <p>按"台风 + 城市"聚合后的<b>群体</b>统计结果——这是问卷数据唯一对外暴露的形态。
 * 各比率字段单位为百分比（0—100）。
 *
 * <p>平台据聚合结果<b>反向优化科普提示</b>（项目描述文档 4.4 节）：
 * 例如 {@code heavyRainWorryRate} 显著高于 {@code supplyPrepRate} 时，
 * 说明公众"担心暴雨但不做准备"，页面应加强物资准备科普。
 * 该判断由 {@code SurveyService#buildHint} 产出 {@code hint} 文案。
 *
 * @author 追风者项目组
 */
public class SurveyAggResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 聚合记录ID（主键，自增） */
    private Long aggId;

    /** 台风ID */
    private Long typhoonId;

    /** 城市ID */
    private Long cityId;

    /** 有效样本量 */
    private Integer totalCount;

    /** 物资储备率（%） */
    private Double supplyPrepRate;

    /** 撤离信息关注率（%） */
    private Double evacuationAttentionRate;

    /** 担心暴雨内涝比例（%） */
    private Double heavyRainWorryRate;

    /** 担心风暴潮比例（%） */
    private Double stormSurgeWorryRate;

    /** 高可信度比例（%） */
    private Double highTrustRate;

    /** 高估风险比例（%） */
    private Double riskOverestimationRate;

    /** 低估风险比例（%） */
    private Double riskUnderestimationRate;

    /** 统计时间 */
    private LocalDateTime statTime;

    /* 以下为联表查询携带，非本表列 */

    /** 城市名称 */
    private String cityName;

    /** 城市所属省份 */
    private String province;

    /** 台风中文名 */
    private String typhoonName;

    public SurveyAggResult() {
    }

    public Long getAggId() {
        return aggId;
    }

    public void setAggId(Long aggId) {
        this.aggId = aggId;
    }

    public Long getTyphoonId() {
        return typhoonId;
    }

    public void setTyphoonId(Long typhoonId) {
        this.typhoonId = typhoonId;
    }

    public Long getCityId() {
        return cityId;
    }

    public void setCityId(Long cityId) {
        this.cityId = cityId;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public Double getSupplyPrepRate() {
        return supplyPrepRate;
    }

    public void setSupplyPrepRate(Double supplyPrepRate) {
        this.supplyPrepRate = supplyPrepRate;
    }

    public Double getEvacuationAttentionRate() {
        return evacuationAttentionRate;
    }

    public void setEvacuationAttentionRate(Double evacuationAttentionRate) {
        this.evacuationAttentionRate = evacuationAttentionRate;
    }

    public Double getHeavyRainWorryRate() {
        return heavyRainWorryRate;
    }

    public void setHeavyRainWorryRate(Double heavyRainWorryRate) {
        this.heavyRainWorryRate = heavyRainWorryRate;
    }

    public Double getStormSurgeWorryRate() {
        return stormSurgeWorryRate;
    }

    public void setStormSurgeWorryRate(Double stormSurgeWorryRate) {
        this.stormSurgeWorryRate = stormSurgeWorryRate;
    }

    public Double getHighTrustRate() {
        return highTrustRate;
    }

    public void setHighTrustRate(Double highTrustRate) {
        this.highTrustRate = highTrustRate;
    }

    public Double getRiskOverestimationRate() {
        return riskOverestimationRate;
    }

    public void setRiskOverestimationRate(Double riskOverestimationRate) {
        this.riskOverestimationRate = riskOverestimationRate;
    }

    public Double getRiskUnderestimationRate() {
        return riskUnderestimationRate;
    }

    public void setRiskUnderestimationRate(Double riskUnderestimationRate) {
        this.riskUnderestimationRate = riskUnderestimationRate;
    }

    public LocalDateTime getStatTime() {
        return statTime;
    }

    public void setStatTime(LocalDateTime statTime) {
        this.statTime = statTime;
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

    public String getTyphoonName() {
        return typhoonName;
    }

    public void setTyphoonName(String typhoonName) {
        this.typhoonName = typhoonName;
    }
}
