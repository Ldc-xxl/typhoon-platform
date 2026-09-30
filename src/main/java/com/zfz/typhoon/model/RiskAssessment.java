package com.zfz.typhoon.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 城市风险评估实体（对应表 {@code risk_assessments}）。
 *
 * <p>用户对"某城市 + 某台风"发起一次风险预评估后落库。风险等级
 * {@link #riskLevel} 取 LOW / MEDIUM / HIGH / EXTREME，
 * 与评分 {@link #riskScore}（0—100）一并给出，前端用颜色区分。
 *
 * <p>合规要求（项目描述文档 · 七、项目约束）：评估结果只基于公开预报数据做
 * 简易规则推算，<b>页面必须固定展示免责提示</b>，
 * 文案见 {@link #DISCLAIMER}。
 *
 * @author 追风者项目组
 */
public class RiskAssessment implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 固定的免责声明，前端必须随评估结果一起展示 */
    public static final String DISCLAIMER =
            "评估结果仅供参考，实际预警请以当地气象部门和应急管理部门发布的官方信息为准。";

    /** 评估ID（主键，自增） */
    private Long assessmentId;

    /** 台风ID */
    private Long typhoonId;

    /** 城市ID */
    private Long cityId;

    /** 评估用户ID */
    private Long userId;

    /** 评估时间 */
    private LocalDateTime assessmentTime;

    /** 台风中心距城市距离（km） */
    private Double distanceKm;

    /** 预计到达时间 */
    private LocalDateTime etaTime;

    /** 受影响开始时段 */
    private LocalDateTime affectedStartTime;

    /** 受影响结束时段 */
    private LocalDateTime affectedEndTime;

    /** 风险等级：LOW / MEDIUM / HIGH / EXTREME */
    private String riskLevel;

    /** 风险评分（0—100） */
    private Double riskScore;

    /** 风险提示建议 */
    private String suggestion;

    /** 创建时间 */
    private LocalDateTime createdAt;

    public RiskAssessment() {
    }

    /**
     * 风险等级的中文名。
     *
     * @return 低 / 中 / 高 / 极高
     */
    public String getRiskLevelText() {
        if (riskLevel == null) {
            return "中";
        }
        switch (riskLevel.toUpperCase()) {
            case "LOW":
                return "低";
            case "MEDIUM":
                return "中";
            case "HIGH":
                return "高";
            case "EXTREME":
                return "极高";
            default:
                return "中";
        }
    }

    public Long getAssessmentId() {
        return assessmentId;
    }

    public void setAssessmentId(Long assessmentId) {
        this.assessmentId = assessmentId;
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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDateTime getAssessmentTime() {
        return assessmentTime;
    }

    public void setAssessmentTime(LocalDateTime assessmentTime) {
        this.assessmentTime = assessmentTime;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public LocalDateTime getEtaTime() {
        return etaTime;
    }

    public void setEtaTime(LocalDateTime etaTime) {
        this.etaTime = etaTime;
    }

    public LocalDateTime getAffectedStartTime() {
        return affectedStartTime;
    }

    public void setAffectedStartTime(LocalDateTime affectedStartTime) {
        this.affectedStartTime = affectedStartTime;
    }

    public LocalDateTime getAffectedEndTime() {
        return affectedEndTime;
    }

    public void setAffectedEndTime(LocalDateTime affectedEndTime) {
        this.affectedEndTime = affectedEndTime;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Double riskScore) {
        this.riskScore = riskScore;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
