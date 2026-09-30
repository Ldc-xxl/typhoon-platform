package com.zfz.typhoon.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 预报分歧分析记录实体（对应表 {@code forecast_disagree_records}）。
 *
 * <p>对应项目描述文档「核心特色功能一：多机构集合预报分歧挖掘与不确定性解析引擎」。
 *
 * <p>系统对同一台风的多家机构预报，在<b>四个维度</b>上量化分歧：
 * <ol>
 *   <li>登陆地点偏差（km）—— {@link #landingPointDeviationKm}</li>
 *   <li>登陆时间偏差（小时）—— {@link #landingTimeDeviationHours}</li>
 *   <li>峰值强度偏差（级）—— {@link #peakIntensityDeviation}</li>
 *   <li>移动速度偏差（km/h）—— {@link #moveSpeedDeviation}</li>
 * </ol>
 * 综合成 {@link #varianceScore} 与归一化分歧指数；超过阈值时置
 * {@link #highUncertainty} 为 true，页面显著标注"高不确定性"。
 *
 * @author 追风者项目组
 */
public class ForecastDisagreeRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 分歧记录ID（主键，自增） */
    private Long recordId;

    /** 台风ID */
    private Long typhoonId;

    /** 分析时间 */
    private LocalDateTime analysisTime;

    /** 登陆点偏差（km） */
    private Double landingPointDeviationKm;

    /** 登陆时间偏差（小时） */
    private Double landingTimeDeviationHours;

    /** 峰值强度偏差（级） */
    private Double peakIntensityDeviation;

    /** 移动速度偏差（km/h） */
    private Double moveSpeedDeviation;

    /** 综合分歧方差值 */
    private Double varianceScore;

    /** 高不确定性标记：true=是 */
    private Boolean highUncertainty;

    /** 分歧分析明细（JSON） */
    private JsonNode analysisDetail;

    /** 创建时间 */
    private LocalDateTime createdAt;

    public ForecastDisagreeRecord() {
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Long getTyphoonId() {
        return typhoonId;
    }

    public void setTyphoonId(Long typhoonId) {
        this.typhoonId = typhoonId;
    }

    public LocalDateTime getAnalysisTime() {
        return analysisTime;
    }

    public void setAnalysisTime(LocalDateTime analysisTime) {
        this.analysisTime = analysisTime;
    }

    public Double getLandingPointDeviationKm() {
        return landingPointDeviationKm;
    }

    public void setLandingPointDeviationKm(Double landingPointDeviationKm) {
        this.landingPointDeviationKm = landingPointDeviationKm;
    }

    public Double getLandingTimeDeviationHours() {
        return landingTimeDeviationHours;
    }

    public void setLandingTimeDeviationHours(Double landingTimeDeviationHours) {
        this.landingTimeDeviationHours = landingTimeDeviationHours;
    }

    public Double getPeakIntensityDeviation() {
        return peakIntensityDeviation;
    }

    public void setPeakIntensityDeviation(Double peakIntensityDeviation) {
        this.peakIntensityDeviation = peakIntensityDeviation;
    }

    public Double getMoveSpeedDeviation() {
        return moveSpeedDeviation;
    }

    public void setMoveSpeedDeviation(Double moveSpeedDeviation) {
        this.moveSpeedDeviation = moveSpeedDeviation;
    }

    public Double getVarianceScore() {
        return varianceScore;
    }

    public void setVarianceScore(Double varianceScore) {
        this.varianceScore = varianceScore;
    }

    public Boolean getHighUncertainty() {
        return highUncertainty;
    }

    public void setHighUncertainty(Boolean highUncertainty) {
        this.highUncertainty = highUncertainty;
    }

    public JsonNode getAnalysisDetail() {
        return analysisDetail;
    }

    public void setAnalysisDetail(JsonNode analysisDetail) {
        this.analysisDetail = analysisDetail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
