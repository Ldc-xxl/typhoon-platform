package com.zfz.typhoon.model.vo;

import com.zfz.typhoon.model.ForecastAgency;

import java.io.Serializable;

/**
 * 预报机构视图对象（面向前端帮助页机构说明表）。
 *
 * <p>字段名对齐前端 {@code ZFZ.mock.agencies}；其中 {@link #type} 描述接入方式
 * （REST API / 集合预报），帮助页据此说明各机构的数据获取途径。
 *
 * @author 追风者项目组
 */
public class AgencyVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 机构ID */
    private Long id;

    /** 机构代码 */
    private String code;

    /** 机构全称 */
    private String name;

    /** 所属国家 / 地区 */
    private String region;

    /** 地图显示颜色 */
    private String color;

    /** 数据接入方式 */
    private String type;

    public AgencyVO() {
    }

    /**
     * 由实体组装。
     *
     * @param agency 机构实体
     * @return 视图对象
     */
    public static AgencyVO of(ForecastAgency agency) {
        AgencyVO vo = new AgencyVO();
        if (agency == null) {
            return vo;
        }
        vo.id = agency.getAgencyId();
        vo.code = agency.getAgencyCode();
        vo.name = agency.getAgencyName();
        vo.region = agency.getRegion();
        vo.color = agency.getDisplayColor();
        vo.type = "ECMWF".equalsIgnoreCase(agency.getAgencyCode()) ? "集合预报" : "REST API";
        return vo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
