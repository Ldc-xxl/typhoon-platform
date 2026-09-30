package com.zfz.typhoon.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 城市影响记录实体（对应表 {@code city_impact_records}）。
 *
 * <p>由风圈范围分析<b>自动生成</b>：某次台风的某级风圈覆盖到了某个城市时，
 * 写入一条记录，说明「该城市处于几级风圈内、预估风力多少级、影响时段与距离」。
 *
 * <p>与 {@link RiskAssessment} 的区别：
 * <ul>
 *   <li>本表是<b>系统自动产出</b>的客观空间分析结果（不需要用户操作）；</li>
 *   <li>{@code risk_assessments} 是<b>用户主动发起</b>的城市风险预评估请求记录，
 *       除空间关系外还含风险评分与防范建议。</li>
 * </ul>
 *
 * <p>对应前端 P1 的「7 级风圈影响城市」卡片。
 *
 * @author 追风者项目组
 */
public class CityImpactRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 影响记录ID（主键，自增） */
    private Long impactId;

    /** 台风ID */
    private Long typhoonId;

    /** 城市ID */
    private Long cityId;

    /** 所在风圈层级（7 / 10 / 12） */
    private Integer windCircleLevel;

    /** 预估风力等级（级） */
    private Integer estimatedWindForce;

    /** 影响开始时间 */
    private LocalDateTime impactStartTime;

    /** 影响结束时间 */
    private LocalDateTime impactEndTime;

    /** 城市距台风中心距离（km） */
    private Double distanceKm;

    /** 记录生成时间 */
    private LocalDateTime generatedAt;

    public CityImpactRecord() {
    }

    public Long getImpactId() {
        return impactId;
    }

    public void setImpactId(Long impactId) {
        this.impactId = impactId;
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

    public Integer getWindCircleLevel() {
        return windCircleLevel;
    }

    public void setWindCircleLevel(Integer windCircleLevel) {
        this.windCircleLevel = windCircleLevel;
    }

    public Integer getEstimatedWindForce() {
        return estimatedWindForce;
    }

    public void setEstimatedWindForce(Integer estimatedWindForce) {
        this.estimatedWindForce = estimatedWindForce;
    }

    public LocalDateTime getImpactStartTime() {
        return impactStartTime;
    }

    public void setImpactStartTime(LocalDateTime impactStartTime) {
        this.impactStartTime = impactStartTime;
    }

    public LocalDateTime getImpactEndTime() {
        return impactEndTime;
    }

    public void setImpactEndTime(LocalDateTime impactEndTime) {
        this.impactEndTime = impactEndTime;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }
}
