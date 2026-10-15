package com.zfz.typhoon.model.vo;

import com.zfz.typhoon.model.RiskAssessment;
import com.zfz.typhoon.util.DateUtil;
import com.zfz.typhoon.util.GeoUtil;

import java.io.Serializable;

/**
 * 城市风险预评估视图对象（面向前端 P4 风险评估页）。
 *
 * <p>字段名对齐接口文档 3.9 节示例。风险等级既给英文字段
 * {@link #level}（LOW / MEDIUM / HIGH / EXTREME，供前端配色映射），
 * 也给中文 {@link #riskLevel}（供直接展示），两者同时存在避免前端再写映射表。
 *
 * @author 追风者项目组
 */
public class RiskAssessmentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 城市ID */
    private Long cityId;

    /** 城市名称 */
    private String cityName;

    /** 所属省份 */
    private String province;

    /** 距台风中心距离（km） */
    private Double distance;

    /** 预计到达时间（yyyy-MM-dd HH:mm） */
    private String eta;

    /** 预计影响风力，如「12 级」 */
    private String windLevel;

    /** 风险等级中文：低 / 中 / 高 / 极高 */
    private String riskLevel;

    /** 风险等级英文代号：LOW / MEDIUM / HIGH / EXTREME */
    private String level;

    /** 风险评分（0—100） */
    private Double riskScore;

    /** 防范建议 */
    private String advice;

    /** 受影响开始时段 */
    private String affectedStart;

    /** 受影响结束时段 */
    private String affectedEnd;

    /** 是否处于风圈范围内 */
    private Boolean inWindCircle;

    /**
     * 本次评估实际使用的<b>基准观测时刻</b>（yyyy-MM-dd HH:mm）。
     *
     * <p>由服务端回传而不是让前端自己拼：ETA 是从这个时刻起算的，
     * 若页面另显示一个"浏览器当前时间"，两边就会各说各话
     * （本项目早先正是如此——页面上的基准时间每刷新一次漂一次，
     * 而推算起点其实是台风的最后实测时刻）。
     *
     * <p>重演模式下它等于所选观测时次；服务端做了容错回落时，
     * 它反映的是<b>实际用到</b>的那个时刻。
     */
    private String baseTime;

    /** 免责提示 */
    private String disclaimer = RiskAssessment.DISCLAIMER;

    public RiskAssessmentVO() {
    }

    /**
     * 由实体组装（联表带出城市信息）。
     *
     * @param entity   评估实体
     * @param cityName 城市名
     * @param province 省份
     * @return 视图对象
     */
    public static RiskAssessmentVO of(RiskAssessment entity, String cityName, String province) {
        RiskAssessmentVO vo = new RiskAssessmentVO();
        vo.cityId = entity.getCityId();
        vo.cityName = cityName;
        vo.province = province;
        vo.distance = entity.getDistanceKm();
        vo.eta = DateUtil.format(entity.getEtaTime());
        vo.riskLevel = entity.getRiskLevelText();
        vo.level = entity.getRiskLevel();
        vo.riskScore = entity.getRiskScore();
        vo.advice = entity.getSuggestion();
        vo.affectedStart = DateUtil.format(entity.getAffectedStartTime());
        vo.affectedEnd = DateUtil.format(entity.getAffectedEndTime());
        // 落库记录里的基准时刻（旧数据无此列时为 null，前端显示「—」即可）
        vo.baseTime = DateUtil.format(entity.getObsTime());
        return vo;
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

    public Double getDistance() {
        return distance;
    }

    public void setDistance(Double distance) {
        this.distance = distance;
    }

    public String getEta() {
        return eta;
    }

    public void setEta(String eta) {
        this.eta = eta;
    }

    public String getWindLevel() {
        return windLevel;
    }

    public void setWindLevel(String windLevel) {
        this.windLevel = windLevel;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public Double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Double riskScore) {
        this.riskScore = riskScore;
    }

    public String getAdvice() {
        return advice;
    }

    public void setAdvice(String advice) {
        this.advice = advice;
    }

    public String getAffectedStart() {
        return affectedStart;
    }

    public void setAffectedStart(String affectedStart) {
        this.affectedStart = affectedStart;
    }

    public String getAffectedEnd() {
        return affectedEnd;
    }

    public void setAffectedEnd(String affectedEnd) {
        this.affectedEnd = affectedEnd;
    }

    public Boolean getInWindCircle() {
        return inWindCircle;
    }

    public void setInWindCircle(Boolean inWindCircle) {
        this.inWindCircle = inWindCircle;
    }

    public String getBaseTime() {
        return baseTime;
    }

    public void setBaseTime(String baseTime) {
        this.baseTime = baseTime;
    }

    public String getDisclaimer() {
        return disclaimer;
    }

    public void setDisclaimer(String disclaimer) {
        this.disclaimer = disclaimer;
    }

    /**
     * 便捷：按风向与距离估算到达时间的小时数（供 Service 内部使用）。
     *
     * @param distanceKm 距离（km）
     * @param moveSpeed  移速（km/h）
     * @return 小时数；移速非法时返回 0
     */
    public static double etaHours(double distanceKm, double moveSpeed) {
        if (moveSpeed <= 0.5D) {
            return 0D;
        }
        return GeoUtil.round(distanceKm / moveSpeed, 1);
    }
}
