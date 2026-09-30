package com.zfz.typhoon.model;

import java.io.Serializable;

/**
 * 城市信息实体（对应表 {@code cities}）。
 *
 * <p>台风影响分析的空间基准：风圈覆盖判定、城市风险预评估、
 * 问卷按城市聚合、相似案例灾情关联，全部以本表的经纬度为基准计算。
 *
 * <p>{@link #baseRiskLevel} 是城市的<b>基准</b>台风风险等级（1 低 / 2 中 / 3 高 / 4 极高），
 * 属于城市固有属性（沿海与否、人口密度、历史受灾频次）；
 * 而针对某一次台风的<b>实时</b>风险等级由
 * {@code com.zfz.typhoon.service.impl.RiskAssessmentServiceImpl} 按
 * "基准风险 + 距离 + 预报强度"综合推算，两者不是一回事。
 *
 * @author 追风者项目组
 */
public class City implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 城市ID（主键，自增） */
    private Long cityId;

    /** 城市名称 */
    private String cityName;

    /** 所属省份 / 直辖市 */
    private String province;

    /** 纬度（北纬为正） */
    private Double latitude;

    /** 经度（东经为正） */
    private Double longitude;

    /** 常住人口（万人） */
    private Integer population;

    /** 是否沿海：true=是 */
    private Boolean coastal;

    /** 台风基准风险等级：1=低，2=中，3=高，4=极高 */
    private Integer baseRiskLevel;

    /** 状态：true=启用，false=停用 */
    private Boolean status;

    public City() {
    }

    /**
     * 基准风险等级的中文名。
     *
     * @return 低 / 中 / 高 / 极高
     */
    public String getBaseRiskLevelText() {
        if (baseRiskLevel == null) {
            return "中";
        }
        switch (baseRiskLevel) {
            case 1:
                return "低";
            case 2:
                return "中";
            case 3:
                return "高";
            case 4:
                return "极高";
            default:
                return "中";
        }
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

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Integer getPopulation() {
        return population;
    }

    public void setPopulation(Integer population) {
        this.population = population;
    }

    public Boolean getCoastal() {
        return coastal;
    }

    public void setCoastal(Boolean coastal) {
        this.coastal = coastal;
    }

    public Integer getBaseRiskLevel() {
        return baseRiskLevel;
    }

    public void setBaseRiskLevel(Integer baseRiskLevel) {
        this.baseRiskLevel = baseRiskLevel;
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "City{" + cityName + " · " + province + '}';
    }
}
