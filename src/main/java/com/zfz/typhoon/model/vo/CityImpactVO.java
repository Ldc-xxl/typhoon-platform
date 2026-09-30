package com.zfz.typhoon.model.vo;

import java.io.Serializable;

/**
 * 风圈影响城市视图对象（面向前端 P1「7 级风圈影响城市」卡片）。
 *
 * <p>字段名对齐前端 {@code ZFZ.mock.affectedCities}：
 * 卡片每行显示「城市 · 省份　邻近 xxx km · 预报 n 级」，
 * 其中预告风力等级用 {@link #color} 着色（等级越高越偏红）。
 *
 * @author 追风者项目组
 */
public class CityImpactVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 城市名称 */
    private String city;

    /** 所属省份 */
    private String province;

    /** 距台风中心距离（km） */
    private Double distance;

    /** 预估受影响风力等级（级） */
    private Integer expectLevel;

    /** 等级配色 */
    private String color;

    /** 所在风圈层级（7 / 10 / 12） */
    private Integer circleLevel;

    /** 影响开始时间 */
    private String startTime;

    /** 影响结束时间 */
    private String endTime;

    public CityImpactVO() {
    }

    /**
     * 按预估风力等级取配色。
     *
     * <p>与设计稿右栏卡片一致：8 级及以上偏红、7 级偏橙、6 级及以下偏绿。
     *
     * @param windLevel 预估风力等级
     * @return 十六进制颜色
     */
    public static String colorOf(Integer windLevel) {
        if (windLevel == null) {
            return "#2FA84F";
        }
        if (windLevel >= 10) {
            return "#E8453C";
        }
        if (windLevel >= 8) {
            return "#E8833A";
        }
        if (windLevel >= 7) {
            return "#C77400";
        }
        return "#2FA84F";
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
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

    public Integer getExpectLevel() {
        return expectLevel;
    }

    public void setExpectLevel(Integer expectLevel) {
        this.expectLevel = expectLevel;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Integer getCircleLevel() {
        return circleLevel;
    }

    public void setCircleLevel(Integer circleLevel) {
        this.circleLevel = circleLevel;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }
}
