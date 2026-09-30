package com.zfz.typhoon.model;

import java.io.Serializable;

/**
 * 预报机构实体（对应表 {@code forecast_agencies}）。
 *
 * <p>维护 CMA（中央气象台）、JMA（日本气象厅）、JTWC（美国联合台风警报中心）
 * 等机构的名称与地图显示颜色。多机构预报路径在地图上用颜色区分，
 * 颜色即取自本表的 {@link #displayColor}。
 *
 * <p>平台角色说明（项目描述文档 · 系统角色）：本表属</p>
 *
 * @author 追风者项目组
 */
public class ForecastAgency implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 机构ID（主键，自增） */
    private Long agencyId;

    /** 机构代码（CMA/JMA/JTWC/HKO/KMA/ECMWF…，唯一） */
    private String agencyCode;

    /** 机构全称 */
    private String agencyName;

    /** 所属国家 / 地区 */
    private String region;

    /** 地图路径显示颜色（十六进制，如 #E60012） */
    private String displayColor;

    /** 官方网站 */
    private String officialUrl;

    /** 机构简介 */
    private String description;

    /** 状态：true=启用，false=停用 */
    private Boolean status;

    public ForecastAgency() {
    }

    public Long getAgencyId() {
        return agencyId;
    }

    public void setAgencyId(Long agencyId) {
        this.agencyId = agencyId;
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

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getDisplayColor() {
        return displayColor;
    }

    public void setDisplayColor(String displayColor) {
        this.displayColor = displayColor;
    }

    public String getOfficialUrl() {
        return officialUrl;
    }

    public void setOfficialUrl(String officialUrl) {
        this.officialUrl = officialUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "ForecastAgency{" + agencyCode + " " + agencyName + '}';
    }
}
