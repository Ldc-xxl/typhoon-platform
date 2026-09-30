package com.zfz.typhoon.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 预报路径点实体（对应表 {@code forecast_paths}）。
 *
 * <p>存储各机构在某个<b>发布时间</b>（{@link #publishTime}）给出的、
 * 针对某个<b>预报时刻</b>（{@link #forecastTime}）的路径点。
 *
 * <p>业务约束（接口文档 3.6 节，后端必须保持）：
 * <ul>
 *   <li>同一台风的多家机构预报，其<b>起点必须一致</b>（都是当前实况位置）；
 *       已经走过的路径各机构不会有分歧，分歧只出现在预报段；</li>
 *   <li>取"最新一次预报"时按 {@code publish_time} 倒序，且同一机构只取最新一批，
 *       否则会把历史发布的旧预报与新预报混在一条线上。</li>
 * </ul>
 *
 * <p>实体本身是"数据库结构"的映射，条目按时间升序排列、按机构分组、
 * 起点对齐等"业务整理"动作在
 * {@code com.zfz.typhoon.service.impl.ForecastPathServiceImpl} 中完成。
 *
 * @author 追风者项目组
 */
public class ForecastPath implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 预报点ID（主键，自增） */
    private Long forecastPointId;

    /** 台风ID */
    private Long typhoonId;

    /** 预报机构ID */
    private Long agencyId;

    /** 预报发布时间 */
    private LocalDateTime publishTime;

    /** 预报目标时刻 */
    private LocalDateTime forecastTime;

    /** 预报纬度 */
    private Double latitude;

    /** 预报经度 */
    private Double longitude;

    /** 预报中心最大风力（级） */
    private Integer forecastWindForce;

    /** 预报中心最大风速（m/s） */
    private Integer forecastWindSpeed;

    /** 预报中心气压（hPa） */
    private Integer forecastPressure;

    /* 以下为联表查询时携带的机构信息，非本表列 */

    /** 机构代码（取自 {@code forecast_agencies.agency_code}） */
    private String agencyCode;

    /** 机构全称（取自 {@code forecast_agencies.agency_name}） */
    private String agencyName;

    /** 机构显示颜色（取自 {@code forecast_agencies.display_color}） */
    private String displayColor;

    public ForecastPath() {
    }

    public Long getForecastPointId() {
        return forecastPointId;
    }

    public void setForecastPointId(Long forecastPointId) {
        this.forecastPointId = forecastPointId;
    }

    public Long getTyphoonId() {
        return typhoonId;
    }

    public void setTyphoonId(Long typhoonId) {
        this.typhoonId = typhoonId;
    }

    public Long getAgencyId() {
        return agencyId;
    }

    public void setAgencyId(Long agencyId) {
        this.agencyId = agencyId;
    }

    public LocalDateTime getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(LocalDateTime publishTime) {
        this.publishTime = publishTime;
    }

    public LocalDateTime getForecastTime() {
        return forecastTime;
    }

    public void setForecastTime(LocalDateTime forecastTime) {
        this.forecastTime = forecastTime;
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

    public Integer getForecastWindForce() {
        return forecastWindForce;
    }

    public void setForecastWindForce(Integer forecastWindForce) {
        this.forecastWindForce = forecastWindForce;
    }

    public Integer getForecastWindSpeed() {
        return forecastWindSpeed;
    }

    public void setForecastWindSpeed(Integer forecastWindSpeed) {
        this.forecastWindSpeed = forecastWindSpeed;
    }

    public Integer getForecastPressure() {
        return forecastPressure;
    }

    public void setForecastPressure(Integer forecastPressure) {
        this.forecastPressure = forecastPressure;
    }

    public String getAgencyCode() {
        return agencyCode;
    }

    public void setAgencyCode(String agencyCode) {
        this.agencyCode = agencyCode;
    }

    public String getAgencyName() {
        return agencyName;
    }

    public void setAgencyName(String agencyName) {
        this.agencyName = agencyName;
    }

    public String getDisplayColor() {
        return displayColor;
    }

    public void setDisplayColor(String displayColor) {
        this.displayColor = displayColor;
    }
}
