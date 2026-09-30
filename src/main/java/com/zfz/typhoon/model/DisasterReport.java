package com.zfz.typhoon.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 灾情报告实体（对应表 {@code disaster_reports}）。
 *
 * <p>关联某次历史台风的公开灾情信息。是"历史台风-城市灾害归因匹配"
 * （项目描述文档 4.2 节）的终点数据：匹配到历史相似台风后，
 * 自动带出本表记录——暴雨量级、积水、风暴潮、典型受灾场景、当年预警处置。
 *
 * <p><b>合规提示</b>：历史灾情仅作类比参考，不代表本次台风一定会发生同等灾害，
 * 页面必须附带醒目标注。
 *
 * @author 追风者项目组
 */
public class DisasterReport implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 灾情报告ID（主键，自增） */
    private Long reportId;

    /** 台风ID */
    private Long typhoonId;

    /** 影响省份 */
    private String affectedProvinces;

    /** 主要影响城市ID */
    private Long mainCityId;

    /** 直接经济损失（亿元） */
    private Double directEconomicLoss;

    /** 死亡及失踪人数 */
    private Integer casualties;

    /** 受伤人数 */
    private Integer injured;

    /** 受灾人口（万人） */
    private Integer affectedPopulation;

    /** 灾情综述 */
    private String disasterSummary;

    /** 报告来源 */
    private String reportSource;

    /** 报告时间 */
    private LocalDateTime reportTime;

    /* 以下为联表查询携带，非本表列 */

    /** 主要影响城市名 */
    private String mainCityName;

    public DisasterReport() {
    }

    public Long getReportId() {
        return reportId;
    }

    public void setReportId(Long reportId) {
        this.reportId = reportId;
    }

    public Long getTyphoonId() {
        return typhoonId;
    }

    public void setTyphoonId(Long typhoonId) {
        this.typhoonId = typhoonId;
    }

    public String getAffectedProvinces() {
        return affectedProvinces;
    }

    public void setAffectedProvinces(String affectedProvinces) {
        this.affectedProvinces = affectedProvinces;
    }

    public Long getMainCityId() {
        return mainCityId;
    }

    public void setMainCityId(Long mainCityId) {
        this.mainCityId = mainCityId;
    }

    public Double getDirectEconomicLoss() {
        return directEconomicLoss;
    }

    public void setDirectEconomicLoss(Double directEconomicLoss) {
        this.directEconomicLoss = directEconomicLoss;
    }

    public Integer getCasualties() {
        return casualties;
    }

    public void setCasualties(Integer casualties) {
        this.casualties = casualties;
    }

    public Integer getInjured() {
        return injured;
    }

    public void setInjured(Integer injured) {
        this.injured = injured;
    }

    public Integer getAffectedPopulation() {
        return affectedPopulation;
    }

    public void setAffectedPopulation(Integer affectedPopulation) {
        this.affectedPopulation = affectedPopulation;
    }

    public String getDisasterSummary() {
        return disasterSummary;
    }

    public void setDisasterSummary(String disasterSummary) {
        this.disasterSummary = disasterSummary;
    }

    public String getReportSource() {
        return reportSource;
    }

    public void setReportSource(String reportSource) {
        this.reportSource = reportSource;
    }

    public LocalDateTime getReportTime() {
        return reportTime;
    }

    public void setReportTime(LocalDateTime reportTime) {
        this.reportTime = reportTime;
    }

    public String getMainCityName() {
        return mainCityName;
    }

    public void setMainCityName(String mainCityName) {
        this.mainCityName = mainCityName;
    }
}
