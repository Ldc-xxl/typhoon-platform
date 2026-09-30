package com.zfz.typhoon.model.vo;

import com.zfz.typhoon.model.City;

import java.io.Serializable;

/**
 * 城市视图对象（面向前端地图点位与城市选择器）。
 *
 * <p>字段名对齐前端 {@code ZFZ.mock.cities}，其中：
 * <ul>
 *   <li>{@link #label} 为 true 时地图上显示城市名标签（避免全国城市挤成一团）；</li>
 *   <li>{@link #affected} 为 true 表示该城市处于本次台风风圈影响范围内，
 *       地图上用高亮样式区分。</li>
 * </ul>
 *
 * @author 追风者项目组
 */
public class CityVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 城市ID */
    private Long id;

    /** 城市名称 */
    private String name;

    /** 所属省份 */
    private String province;

    /** 经度 */
    private Double lon;

    /** 纬度 */
    private Double lat;

    /** 是否显示地图标签 */
    private Boolean label;

    /** 是否处于风圈影响范围 */
    private Boolean affected;

    /** 常住人口（万人） */
    private Integer population;

    /** 基准风险等级（1—4） */
    private Integer baseRiskLevel;

    /** 基准风险等级中文名 */
    private String baseRiskLevelText;

    public CityVO() {
    }

    /**
     * 由实体组装。
     *
     * @param city 城市实体
     * @return 视图对象
     */
    public static CityVO of(City city) {
        CityVO vo = new CityVO();
        if (city == null) {
            return vo;
        }
        vo.id = city.getCityId();
        vo.name = city.getCityName();
        vo.province = city.getProvince();
        vo.lon = city.getLongitude();
        vo.lat = city.getLatitude();
        vo.population = city.getPopulation();
        vo.baseRiskLevel = city.getBaseRiskLevel();
        vo.baseRiskLevelText = city.getBaseRiskLevelText();
        vo.label = true;
        vo.affected = false;
        return vo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public Double getLon() {
        return lon;
    }

    public void setLon(Double lon) {
        this.lon = lon;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Boolean getLabel() {
        return label;
    }

    public void setLabel(Boolean label) {
        this.label = label;
    }

    public Boolean getAffected() {
        return affected;
    }

    public void setAffected(Boolean affected) {
        this.affected = affected;
    }

    public Integer getPopulation() {
        return population;
    }

    public void setPopulation(Integer population) {
        this.population = population;
    }

    public Integer getBaseRiskLevel() {
        return baseRiskLevel;
    }

    public void setBaseRiskLevel(Integer baseRiskLevel) {
        this.baseRiskLevel = baseRiskLevel;
    }

    public String getBaseRiskLevelText() {
        return baseRiskLevelText;
    }

    public void setBaseRiskLevelText(String baseRiskLevelText) {
        this.baseRiskLevelText = baseRiskLevelText;
    }
}
